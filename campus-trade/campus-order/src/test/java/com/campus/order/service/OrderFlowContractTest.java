package com.campus.order.service;

import com.campus.order.feign.ProductFeignClient;
import com.campus.order.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OrderFlowContractTest {

    @Test
    void createOrderKeepsLocalOrderWriteTransactional() throws Exception {
        Method method = OrderServiceImpl.class.getMethod("createOrder", Long.class, Long.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertNotNull(transactional, "createOrder must roll back local order row when later steps fail");
        assertEquals(1, transactional.rollbackFor().length);
        assertEquals(Exception.class, transactional.rollbackFor()[0]);
    }

    @Test
    void productDeductFeignExposesSeckillCachePreserveFlag() throws Exception {
        Method method = ProductFeignClient.class.getMethod("deductStock", Long.class, boolean.class);
        Annotation[] annotations = method.getParameterAnnotations()[1];

        RequestParam requestParam = null;
        for (Annotation annotation : annotations) {
            if (annotation instanceof RequestParam rp) {
                requestParam = rp;
                break;
            }
        }

        assertNotNull(requestParam, "preserveSeckillCache must be sent as a request parameter");
        assertEquals("preserveSeckillCache", requestParam.value());
        assertEquals("false", requestParam.defaultValue());
    }
}
