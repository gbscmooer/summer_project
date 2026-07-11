package com.campus.product.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProductInternalContractTest {

    @Test
    void deductStockAcceptsOptionalSeckillCachePreserveFlag() throws Exception {
        Method method = ProductController.class.getMethod("deductStock", String.class, Long.class, String.class, boolean.class);
        Annotation[] annotations = method.getParameterAnnotations()[3];

        RequestParam requestParam = null;
        for (Annotation annotation : annotations) {
            if (annotation instanceof RequestParam rp) {
                requestParam = rp;
                break;
            }
        }

        assertNotNull(requestParam, "deductStock must expose preserveSeckillCache as optional request param");
        assertEquals("preserveSeckillCache", requestParam.value());
        assertEquals("false", requestParam.defaultValue());
    }
}
