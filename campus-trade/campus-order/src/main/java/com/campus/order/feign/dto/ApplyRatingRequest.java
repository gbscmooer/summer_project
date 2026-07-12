package com.campus.order.feign.dto;

import lombok.Data;

@Data
public class ApplyRatingRequest {
    private Long sellerId;
    private Integer rating;
}
