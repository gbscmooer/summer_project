package com.campus.product.dto;

import com.campus.product.entity.TopicPost;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TopicPostVO {

    private Long postId;
    private Long userId;
    private String nickname;
    private String title;
    private String content;
    private LocalDateTime createTime;
    private Integer productCount;
    private List<ProductListVO> products;

    public static TopicPostVO from(TopicPost post) {
        TopicPostVO vo = new TopicPostVO();
        vo.setPostId(post.getId());
        vo.setUserId(post.getUserId());
        vo.setTitle(post.getTitle());
        vo.setContent(post.getContent());
        vo.setCreateTime(post.getCreateTime());
        return vo;
    }
}
