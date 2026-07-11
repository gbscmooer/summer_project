package com.campus.product.ai.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AiSearchIntent {
    private String keyword;
    private String category;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private String sort;
    private String explanation;
}
