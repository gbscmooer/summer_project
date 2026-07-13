package com.campus.order.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.campus.common.exception.BizException;
import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import com.campus.common.util.PageParamUtil;
import com.campus.order.dto.OrderReviewRequest;
import com.campus.order.dto.OrderReviewVO;
import com.campus.order.entity.Order;
import com.campus.order.entity.OrderReview;
import com.campus.order.feign.UserFeignClient;
import com.campus.order.feign.dto.ApplyRatingRequest;
import com.campus.order.feign.dto.UserBriefDTO;
import com.campus.order.mapper.OrderMapper;
import com.campus.order.mapper.OrderReviewMapper;
import com.campus.order.service.OrderReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderReviewServiceImpl extends ServiceImpl<OrderReviewMapper, OrderReview>
        implements OrderReviewService {

    private static final int STATUS_DONE = 2;
    private static final int REVIEW_WINDOW_DAYS = 7;
    private static final int RATING_SYNC_MAX_ATTEMPTS = 3;

    private final OrderMapper orderMapper;
    private final UserFeignClient userFeignClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitReview(Long buyerId, Long orderId, OrderReviewRequest request) {
        if (buyerId == null || orderId == null || request == null || request.getRating() == null) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        int rating = request.getRating();
        if (rating < 1 || rating > 5) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "评分须为1-5");
        }

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        if (!buyerId.equals(order.getBuyerId())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        if (order.getStatus() == null || order.getStatus() != STATUS_DONE) {
            throw new BizException(ResultCode.ORDER_NOT_COMPLETED);
        }

        LocalDateTime completedAt = order.getCompletedAt() != null
                ? order.getCompletedAt()
                : (order.getUpdateTime() != null ? order.getUpdateTime() : order.getCreateTime());
        if (completedAt == null || completedAt.plusDays(REVIEW_WINDOW_DAYS).isBefore(LocalDateTime.now())) {
            throw new BizException(ResultCode.ORDER_REVIEW_EXPIRED);
        }

        OrderReview existing = lambdaQuery()
                .eq(OrderReview::getOrderId, orderId)
                .eq(OrderReview::getBuyerId, buyerId)
                .one();
        if (existing != null) {
            throw new BizException(ResultCode.ORDER_REVIEW_EXISTS);
        }

        OrderReview review = new OrderReview();
        review.setOrderId(orderId);
        review.setProductId(order.getProductId());
        review.setBuyerId(buyerId);
        review.setSellerId(order.getSellerId());
        review.setRating(rating);
        review.setRatingApplied(0);
        String content = request.getContent() == null ? null : request.getContent().trim();
        review.setContent(StringUtils.hasText(content) ? content : null);

        try {
            save(review);
        } catch (DataIntegrityViolationException e) {
            throw new BizException(ResultCode.ORDER_REVIEW_EXISTS);
        }

        Long sellerId = order.getSellerId();
        Long reviewId = review.getId();
        ApplyRatingRequest apply = new ApplyRatingRequest();
        apply.setSellerId(sellerId);
        apply.setRating(rating);
        apply.setReviewId(reviewId);
        // 提交后再调用户服务，避免「用户侧已加分、订单评价事务回滚」
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    syncSellerRating(orderId, sellerId, rating, reviewId, apply);
                }
            });
        } else {
            syncSellerRating(orderId, sellerId, rating, reviewId, apply);
        }

        return review.getId();
    }

    private void syncSellerRating(Long orderId, Long sellerId, int rating, Long reviewId, ApplyRatingRequest apply) {
        Exception last = null;
        for (int attempt = 1; attempt <= RATING_SYNC_MAX_ATTEMPTS; attempt++) {
            try {
                unwrap(userFeignClient.applyRating(apply));
                lambdaUpdate()
                        .eq(OrderReview::getId, reviewId)
                        .eq(OrderReview::getRatingApplied, 0)
                        .set(OrderReview::getRatingApplied, 1)
                        .update();
                return;
            } catch (Exception e) {
                last = e;
                log.warn("卖家信誉同步失败，准备重试. attempt={}/{}, orderId={}, reviewId={}",
                        attempt, RATING_SYNC_MAX_ATTEMPTS, orderId, reviewId, e);
                try {
                    Thread.sleep(50L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.error("评价已落库但卖家信誉更新失败（已重试），需对账. orderId={}, sellerId={}, rating={}, reviewId={}",
                orderId, sellerId, rating, reviewId, last);
    }

    @Override
    public OrderReviewVO getByOrder(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        if (!userId.equals(order.getBuyerId()) && !userId.equals(order.getSellerId())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        OrderReview review = lambdaQuery()
                .eq(OrderReview::getOrderId, orderId)
                .one();
        if (review == null) {
            return null;
        }
        OrderReviewVO vo = OrderReviewVO.from(review);
        enrichBuyerNicknames(List.of(vo));
        return vo;
    }

    @Override
    public PageResult<OrderReviewVO> listBySeller(Long sellerId, Integer pageNum, Integer pageSize) {
        if (sellerId == null || sellerId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        int pageNo = PageParamUtil.normalizePageNum(pageNum);
        int size = PageParamUtil.normalizePageSize(pageSize);
        Page<OrderReview> page = new Page<>(pageNo, size);
        lambdaQuery()
                .eq(OrderReview::getSellerId, sellerId)
                .orderByDesc(OrderReview::getCreateTime)
                .page(page);

        List<OrderReviewVO> list = page.getRecords().stream()
                .map(OrderReviewVO::from)
                .collect(Collectors.toList());
        enrichBuyerNicknames(list);
        return PageResult.of(page.getTotal(), pageNo, size, list);
    }

    private void enrichBuyerNicknames(List<OrderReviewVO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        String ids = list.stream()
                .map(OrderReviewVO::getBuyerId)
                .filter(Objects::nonNull)
                .distinct()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        if (!StringUtils.hasText(ids)) {
            return;
        }
        try {
            Result<List<UserBriefDTO>> result = userFeignClient.batchGetUsers(ids);
            if (result == null
                    || result.getCode() == null
                    || result.getCode() != ResultCode.SUCCESS.getCode()
                    || result.getData() == null
                    || result.getData().isEmpty()) {
                return;
            }
            Map<Long, UserBriefDTO> userMap = result.getData().stream()
                    .filter(u -> u.getUserId() != null)
                    .collect(Collectors.toMap(UserBriefDTO::getUserId, u -> u, (a, b) -> a));
            for (OrderReviewVO vo : list) {
                UserBriefDTO user = userMap.get(vo.getBuyerId());
                if (user != null) {
                    vo.setBuyerNickname(user.getNickname());
                }
            }
        } catch (Exception e) {
            log.warn("补全评价买家昵称失败，降级返回基础评价. ids={}", ids, e);
        }
    }

    private <T> T unwrap(Result<T> result) {
        if (result == null || result.getCode() == null || result.getCode() != ResultCode.SUCCESS.getCode()) {
            int code = result != null && result.getCode() != null ? result.getCode() : ResultCode.INTERNAL_ERROR.getCode();
            String message = result != null && result.getMessage() != null
                    ? result.getMessage()
                    : ResultCode.INTERNAL_ERROR.getMessage();
            throw new BizException(code, message);
        }
        return result.getData();
    }
}
