package com.campus.product.dto;

import com.campus.product.entity.TopicComment;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class TopicCommentVO {

    private Long commentId;
    private Long postId;
    private Long userId;
    private String nickname;
    private String avatar;
    private String bio;
    private Long parentId;
    private String content;
    private String imageUrl;
    private Integer upvoteCount;
    private Boolean upvoted;
    private LocalDateTime createTime;
    private List<TopicCommentVO> children = new ArrayList<>();

    public static TopicCommentVO from(TopicComment comment) {
        TopicCommentVO vo = new TopicCommentVO();
        vo.setCommentId(comment.getId());
        vo.setPostId(comment.getPostId());
        vo.setUserId(comment.getUserId());
        vo.setParentId(comment.getParentId());
        vo.setContent(comment.getContent());
        vo.setImageUrl(comment.getImageUrl());
        vo.setUpvoteCount(comment.getUpvoteCount() == null ? 0 : comment.getUpvoteCount());
        vo.setUpvoted(false);
        vo.setCreateTime(comment.getCreateTime());
        return vo;
    }
}
