package com.campus.product.ai.service;

import com.campus.common.result.PageResult;
import com.campus.product.ai.client.AiModelClient;
import com.campus.product.ai.dto.AiListingDraftRequest;
import com.campus.product.ai.dto.AiListingDraftResponse;
import com.campus.product.ai.dto.AiSearchRequest;
import com.campus.product.ai.dto.AiSearchResponse;
import com.campus.product.ai.service.impl.AiAssistantServiceImpl;
import com.campus.product.dto.ProductListVO;
import com.campus.product.service.ProductImageService;
import com.campus.product.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiAssistantServiceImplTest {

    private AiModelClient aiModelClient;
    private ProductService productService;
    private ProductImageService productImageService;
    private AiAssistantService service;

    @BeforeEach
    void setUp() {
        aiModelClient = mock(AiModelClient.class);
        productService = mock(ProductService.class);
        productImageService = mock(ProductImageService.class);
        service = new AiAssistantServiceImpl(aiModelClient, productService, productImageService, new ObjectMapper());
    }

    @Test
    void convertsNaturalLanguageIntoRealProductSearch() {
        when(aiModelClient.completeJson(any(), any(), any())).thenReturn("""
                {"keyword":"高等数学","category":"教材","minPrice":null,"maxPrice":40,"sort":"price_asc","explanation":"查找40元以内的高数教材"}
                """);
        ProductListVO product = product("高等数学第七版", "20.00");
        when(productService.search(eq("高等数学"), eq("教材"), eq(null), eq(new BigDecimal("40")),
                eq("price_asc"), eq(1), eq(12)))
                .thenReturn(PageResult.of(1L, 1, 12, List.of(product)));

        AiSearchRequest request = new AiSearchRequest();
        request.setQuery("我需要一本高数课程教科书，40元以内");
        AiSearchResponse response = service.search(request);

        assertEquals(1L, response.getTotal());
        assertEquals("高等数学第七版", response.getProducts().get(0).getTitle());
        assertEquals(new BigDecimal("20.00"), response.getPriceLow());
    }

    @Test
    void pricesListingFromComparableProductsAndCondition() {
        when(productImageService.readAsDataUrls(eq(List.of("/api/product/image/a.jpg")), eq(9L)))
                .thenReturn(List.of("data:image/jpeg;base64,AQ=="));
        when(aiModelClient.completeJson(any(), any(), any())).thenReturn("""
                {"title":"高等数学第七版 上册","description":"封面轻微磨损，内页完整。","category":"教材","condition":"良好","keywords":["高等数学第七版"],"referencePrice":35,"confidence":0.92,"warnings":["请确认有无笔记"]}
                """);
        when(productService.search(eq("高等数学第七版"), eq("教材"), eq(null), eq(null),
                eq("price_asc"), eq(1), eq(20)))
                .thenReturn(PageResult.of(3L, 1, 20, List.of(
                        product("同款A", "20.00"), product("同款B", "30.00"), product("同款C", "40.00")
                )));

        AiListingDraftRequest request = new AiListingDraftRequest();
        request.setImages(List.of("/api/product/image/a.jpg"));
        AiListingDraftResponse response = service.createListingDraft(9L, request);

        assertEquals(new BigDecimal("27.00"), response.getSuggestedPrice());
        assertEquals(3, response.getComparableCount());
        assertTrue(response.getDescription().startsWith("【成色】良好"));
        verify(productImageService).readAsDataUrls(request.getImages(), 9L);
    }

    private ProductListVO product(String title, String price) {
        ProductListVO product = new ProductListVO();
        product.setTitle(title);
        product.setPrice(new BigDecimal(price));
        return product;
    }
}
