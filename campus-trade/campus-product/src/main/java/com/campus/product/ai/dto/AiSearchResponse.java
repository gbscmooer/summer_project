package com.campus.product.ai.dto;

import com.campus.product.dto.ProductListVO;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class AiSearchResponse {
    private String query;
    private String summary;
    private AiSearchIntent intent;
    private Long total;
    private BigDecimal priceLow;
    private BigDecimal priceHigh;
    private List<ProductListVO> products;
}
