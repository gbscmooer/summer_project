package com.campus.product.ai.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AiListingAnalysis {
    private String title;
    private String description;
    private String category;
    private String condition;
    private List<String> keywords;
    private BigDecimal referencePrice;
    private Double confidence;
    private List<String> warnings;
}
