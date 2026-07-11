package com.campus.product.config;

import com.campus.product.dto.NullValueMarker;
import com.campus.product.dto.ProductDetailVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RedisConfigSecurityTest {

    @Test
    void allowsOnlyProductCacheDtoTypes() throws Exception {
        ObjectMapper mapper = RedisConfig.buildObjectMapper();
        String allowed = mapper.writeValueAsString(new NullValueMarker());
        assertInstanceOf(NullValueMarker.class, mapper.readValue(allowed, Object.class));

        ProductDetailVO product = new ProductDetailVO();
        product.setPrice(new BigDecimal("12.50"));
        product.setImages(new ArrayList<>(java.util.List.of("/api/product/image/example.jpg")));
        String productJson = mapper.writeValueAsString(product);
        assertInstanceOf(ProductDetailVO.class, mapper.readValue(productJson, Object.class));

        String untrusted = "{\"@class\":\"java.io.File\",\"path\":\"/tmp/payload\"}";
        assertThrows(Exception.class, () -> mapper.readValue(untrusted, Object.class));
    }
}
