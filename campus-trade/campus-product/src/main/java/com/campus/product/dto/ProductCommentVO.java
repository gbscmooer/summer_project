package com.campus.product.dto;

import com.campus.product.entity.ProductComment;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductCommentVO {

    private Long commentId;
    private Long userId;
    private String nickname;
    private String content;
    private LocalDateTime createTime;

    public static ProductCommentVO from(ProductComment comment) {
        ProductCommentVO vo = new ProductCommentVO();
        vo.setCommentId(comment.getId());
        vo.setUserId(comment.getUserId());
        vo.setContent(comment.getContent());
        vo.setCreateTime(comment.getCreateTime());
        return vo;
    }
}
