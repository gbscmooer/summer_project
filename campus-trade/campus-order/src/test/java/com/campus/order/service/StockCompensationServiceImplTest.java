package com.campus.order.service;

import com.campus.common.result.Result;
import com.campus.order.entity.StockCompensationTask;
import com.campus.order.entity.Order;
import com.campus.order.feign.ProductFeignClient;
import com.campus.order.mapper.OrderMapper;
import com.campus.order.mapper.StockCompensationTaskMapper;
import com.campus.order.service.impl.StockCompensationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockCompensationServiceImplTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, StockCompensationTask.class);
        TableInfoHelper.initTableInfo(assistant, Order.class);
    }

    @Test
    void retriesRestoreWhenNoCommittedOrderExists() {
        StockCompensationTaskMapper taskMapper = mock(StockCompensationTaskMapper.class);
        OrderMapper orderMapper = mock(OrderMapper.class);
        ProductFeignClient productFeign = mock(ProductFeignClient.class);
        StockCompensationService transactionalSelf = mock(StockCompensationService.class);
        StockCompensationTask task = pendingTask();
        when(taskMapper.lockDueById(1L)).thenReturn(task);
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(productFeign.restoreStock(10L, "ORDER-1")).thenReturn(Result.success());

        new StockCompensationServiceImpl(taskMapper, orderMapper, productFeign, transactionalSelf).retryOne(1L);

        verify(productFeign).restoreStock(10L, "ORDER-1");
        verify(taskMapper).update(eq(null), any());
    }

    @Test
    void committedOrderCompletesIntentWithoutRestoringStock() {
        StockCompensationTaskMapper taskMapper = mock(StockCompensationTaskMapper.class);
        OrderMapper orderMapper = mock(OrderMapper.class);
        ProductFeignClient productFeign = mock(ProductFeignClient.class);
        StockCompensationService transactionalSelf = mock(StockCompensationService.class);
        when(taskMapper.lockDueById(1L)).thenReturn(pendingTask());
        when(orderMapper.selectCount(any())).thenReturn(1L);

        new StockCompensationServiceImpl(taskMapper, orderMapper, productFeign, transactionalSelf).retryOne(1L);

        verify(productFeign, never()).restoreStock(any(), any());
        verify(taskMapper).update(eq(null), any());
    }

    private StockCompensationTask pendingTask() {
        StockCompensationTask task = new StockCompensationTask();
        task.setId(1L);
        task.setProductId(10L);
        task.setOrderNo("ORDER-1");
        task.setStatus(0);
        task.setAttempts(0);
        task.setNextRetryTime(LocalDateTime.now().minusMinutes(1));
        return task;
    }
}
