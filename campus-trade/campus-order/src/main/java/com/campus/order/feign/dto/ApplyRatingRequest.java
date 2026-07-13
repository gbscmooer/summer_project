package com.campus.order.feign.dto;

import lombok.Data;

@Data
public class ApplyRatingRequest {
    private Long sellerId;
    private Integer rating;
    /** 订单评价主键，用于信誉增量幂等。 */
    private Long reviewId;
}
