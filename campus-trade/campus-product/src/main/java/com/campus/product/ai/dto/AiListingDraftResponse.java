package com.campus.product.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class AiListingDraftResponse {
    private String title;
    private String description;
    private String category;
    private String condition;
    private BigDecimal suggestedPrice;
    private BigDecimal marketPriceLow;
    private BigDecimal marketPriceHigh;
    private Integer comparableCount;
    private String pricingBasis;
    private Double confidence;
    private List<String> warnings;
}
