package com.campus.order.config;

import com.campus.order.feign.dto.ProductDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RedisConfigSecurityTest {

    @Test
    void allowsOnlyOrderCacheDtoTypes() throws Exception {
        ObjectMapper mapper = RedisConfig.buildObjectMapper();
        ProductDTO product = new ProductDTO();
        product.setPrice(new BigDecimal("12.50"));
        String allowed = mapper.writeValueAsString(product);
        assertInstanceOf(ProductDTO.class, mapper.readValue(allowed, Object.class));

        String untrusted = "{\"@class\":\"java.io.File\",\"path\":\"/tmp/payload\"}";
        assertThrows(Exception.class, () -> mapper.readValue(untrusted, Object.class));
    }
}
