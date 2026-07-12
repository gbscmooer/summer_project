package com.campus.order.dto;

import com.campus.order.entity.OrderReview;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrderReviewVO {

    private Long reviewId;
    private Long orderId;
    private Long productId;
    private Long buyerId;
    private String buyerNickname;
    private Long sellerId;
    private Integer rating;
    private String content;
    private LocalDateTime createTime;

    public static OrderReviewVO from(OrderReview review) {
        OrderReviewVO vo = new OrderReviewVO();
        vo.setReviewId(review.getId());
        vo.setOrderId(review.getOrderId());
        vo.setProductId(review.getProductId());
        vo.setBuyerId(review.getBuyerId());
        vo.setSellerId(review.getSellerId());
        vo.setRating(review.getRating());
        vo.setContent(review.getContent());
        vo.setCreateTime(review.getCreateTime());
        return vo;
    }
}
