package com.campus.order.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campus.order.entity.Order;
import com.campus.order.mapper.OrderMapper;
import com.campus.order.service.impl.OrderTimeoutServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderTimeoutServiceImplTest {

    @BeforeAll
    static void initMybatisLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Order.class);
    }

    @Test
    void closeExpiredUnpaidOrdersClosesCandidatesAndSkipsFailures() {
        OrderMapper orderMapper = mock(OrderMapper.class);
        OrderService orderService = mock(OrderService.class);

        Order due = new Order();
        due.setId(1L);
        due.setOrderNo("O-1");
        due.setStatus(0);
        due.setCreateTime(LocalDateTime.now().minusMinutes(30));

        Order raced = new Order();
        raced.setId(2L);
        raced.setOrderNo("O-2");
        raced.setStatus(0);
        raced.setCreateTime(LocalDateTime.now().minusMinutes(30));

        when(orderMapper.selectList(any())).thenReturn(List.of(due, raced));
        when(orderService.closeUnpaidBySystem(1L)).thenReturn(true);
        when(orderService.closeUnpaidBySystem(2L)).thenReturn(false);

        OrderTimeoutServiceImpl service = new OrderTimeoutServiceImpl(orderMapper, orderService);
        ReflectionTestUtils.setField(service, "unpaidTimeoutMinutes", 15);
        ReflectionTestUtils.setField(service, "timeoutBatchSize", 100);

        assertEquals(1, service.closeExpiredUnpaidOrders());
        verify(orderService).closeUnpaidBySystem(1L);
        verify(orderService).closeUnpaidBySystem(2L);
    }

    @Test
    void closeExpiredUnpaidOrdersReturnsZeroWhenNoCandidates() {
        OrderMapper orderMapper = mock(OrderMapper.class);
        OrderService orderService = mock(OrderService.class);
        when(orderMapper.selectList(any())).thenReturn(List.of());

        OrderTimeoutServiceImpl service = new OrderTimeoutServiceImpl(orderMapper, orderService);
        ReflectionTestUtils.setField(service, "unpaidTimeoutMinutes", 15);
        ReflectionTestUtils.setField(service, "timeoutBatchSize", 100);

        assertEquals(0, service.closeExpiredUnpaidOrders());
        verify(orderService, never()).closeUnpaidBySystem(any());
    }
}
