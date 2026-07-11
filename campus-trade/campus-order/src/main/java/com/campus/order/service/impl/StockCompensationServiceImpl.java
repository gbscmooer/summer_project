package com.campus.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.order.entity.Order;
import com.campus.order.entity.StockCompensationTask;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import com.campus.order.feign.ProductFeignClient;
import com.campus.order.mapper.OrderMapper;
import com.campus.order.mapper.StockCompensationTaskMapper;
import com.campus.order.service.StockCompensationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.context.annotation.Lazy;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class StockCompensationServiceImpl implements StockCompensationService {

    private static final int PENDING = 0;
    private static final int COMPLETED = 1;
    private final StockCompensationTaskMapper taskMapper;
    private final OrderMapper orderMapper;
    private final ProductFeignClient productFeignClient;
    private final StockCompensationService transactionalSelf;

    public StockCompensationServiceImpl(StockCompensationTaskMapper taskMapper,
                                        OrderMapper orderMapper,
                                        ProductFeignClient productFeignClient,
                                        @Lazy StockCompensationService transactionalSelf) {
        this.taskMapper = taskMapper;
        this.orderMapper = orderMapper;
        this.productFeignClient = productFeignClient;
        this.transactionalSelf = transactionalSelf;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void register(Long productId, String orderNo) {
        StockCompensationTask task = new StockCompensationTask();
        task.setProductId(productId);
        task.setOrderNo(orderNo);
        task.setStatus(PENDING);
        task.setAttempts(0);
        task.setNextRetryTime(LocalDateTime.now().plusMinutes(1));
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        try {
            taskMapper.insert(task);
        } catch (DuplicateKeyException ignored) {
            // The same order operation already has a durable recovery intent.
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForOrderTransaction(String orderNo) {
        if (taskMapper.lockByOrderNo(orderNo) == null) {
            throw new IllegalStateException("stock compensation intent is missing");
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String orderNo) {
        markComplete(orderNo);
    }

    private void markComplete(String orderNo) {
        taskMapper.update(null, new LambdaUpdateWrapper<StockCompensationTask>()
                .eq(StockCompensationTask::getOrderNo, orderNo)
                .set(StockCompensationTask::getStatus, COMPLETED)
                .set(StockCompensationTask::getLastError, null)
                .set(StockCompensationTask::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public void completeAfterCommit(String orderNo) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            transactionalSelf.complete(orderNo);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                transactionalSelf.complete(orderNo);
            }
        });
    }

    @Override
    public void completeAfterCompletion(String orderNo) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            transactionalSelf.complete(orderNo);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                transactionalSelf.complete(orderNo);
            }
        });
    }

    @Scheduled(fixedDelayString = "${campus.stock-compensation.retry-delay-ms:30000}")
    public void retryPending() {
        List<StockCompensationTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<StockCompensationTask>()
                        .eq(StockCompensationTask::getStatus, PENDING)
                        .le(StockCompensationTask::getNextRetryTime, LocalDateTime.now())
                        .orderByAsc(StockCompensationTask::getId)
                        .last("LIMIT 50"));
        for (StockCompensationTask task : tasks) {
            transactionalSelf.retryOne(task.getId());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryOne(Long taskId) {
        StockCompensationTask task = taskMapper.lockDueById(taskId);
        if (task == null) {
            return; // An active order transaction holds the row lock, or another worker won it.
        }
        try {
            Long orderCount = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                    .eq(Order::getOrderNo, task.getOrderNo()));
            if (orderCount != null && orderCount > 0) {
                markComplete(task.getOrderNo());
                return;
            }
            Result<Void> result = productFeignClient.restoreStock(task.getProductId(), task.getOrderNo());
            if (result == null || result.getCode() == null
                    || result.getCode() != ResultCode.SUCCESS.getCode()) {
                throw new IllegalStateException("product service rejected stock compensation");
            }
            markComplete(task.getOrderNo());
        } catch (Exception e) {
                int attempts = task.getAttempts() == null ? 1 : task.getAttempts() + 1;
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                if (message.length() > 255) message = message.substring(0, 255);
                taskMapper.update(null, new LambdaUpdateWrapper<StockCompensationTask>()
                        .eq(StockCompensationTask::getId, task.getId())
                        .eq(StockCompensationTask::getStatus, PENDING)
                        .set(StockCompensationTask::getAttempts, attempts)
                        .set(StockCompensationTask::getLastError, message)
                        .set(StockCompensationTask::getNextRetryTime,
                                LocalDateTime.now().plusSeconds(Math.min(3600, 30L * attempts)))
                        .set(StockCompensationTask::getUpdateTime, LocalDateTime.now()));
                log.error("库存补偿重试失败，orderNo={}, attempts={}", task.getOrderNo(), attempts);
        }
    }
}
