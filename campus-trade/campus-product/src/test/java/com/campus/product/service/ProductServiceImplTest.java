package com.campus.product.service;

import com.campus.common.result.Result;
import com.campus.product.dto.ProductDetailVO;
import com.campus.product.entity.Product;
import com.campus.product.feign.UserFeignClient;
import com.campus.product.feign.dto.UserBriefDTO;
import com.campus.product.mapper.ProductMapper;
import com.campus.product.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void getDetailFillsSellerNicknameFromUserService() {
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        AtomicReference<String> deletedKey = new AtomicReference<>();
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>() {
            @Override
            public ValueOperations<String, Object> opsForValue() {
                return valueOperations;
            }

            @Override
            public Boolean delete(String key) {
                deletedKey.set(key);
                return true;
            }
        };

        UserFeignClient userFeignClient = mock(UserFeignClient.class);
        ProductMapper productMapper = mock(ProductMapper.class);

        Product product = new Product();
        product.setId(10L);
        product.setTitle("算法书");
        product.setDescription("九成新");
        product.setPrice(new BigDecimal("20.00"));
        product.setImages("https://example.com/a.png");
        product.setCategory("教材");
        product.setSellerId(7L);
        product.setStatus(1);
        product.setStock(1);
        product.setViewCount(3);
        product.setCreateTime(LocalDateTime.now());

        UserBriefDTO seller = new UserBriefDTO();
        seller.setUserId(7L);
        seller.setNickname("李四");

        when(productMapper.selectById(10L)).thenReturn(product);
        when(userFeignClient.batchGetUsers("7")).thenReturn(Result.success(List.of(seller)));

        ProductServiceImpl service = new ProductServiceImpl(
                redisTemplate,
                null,
                null,
                userFeignClient);
        ReflectionTestUtils.setField(service, "baseMapper", productMapper);

        ProductDetailVO detail = service.getDetail(10L);

        assertEquals("李四", detail.getSellerNickname());
        assertEquals(4, detail.getViewCount());
        assertEquals("product:lock:10", deletedKey.get());
        verify(productMapper).incrementViewCount(10L);
        verify(valueOperations).set(eq("product:detail:10"), any(ProductDetailVO.class), anyLong(), eq(TimeUnit.MILLISECONDS));
    }
}
