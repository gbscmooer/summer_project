package com.campus.product.dto;

import lombok.Data;

@Data
public class TopicTrendingItemVO {

    private Long postId;
    private String title;
    private String reason;
    private Integer commentCount;
    private Integer upvoteCount;
    private Double heatScore;
}
