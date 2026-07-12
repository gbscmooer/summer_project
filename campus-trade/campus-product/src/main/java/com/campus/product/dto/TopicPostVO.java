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
    private String avatar;
    private String bio;
    private String title;
    private String content;
    private Integer upvoteCount;
    private Integer commentCount;
    private Integer tipTotal;
    /** 作者是否开启打赏 */
    private Boolean tipEnabled;
    private Boolean upvoted;
    private LocalDateTime createTime;
    private Integer productCount;
    private List<ProductListVO> products;

    public static TopicPostVO from(TopicPost post) {
        TopicPostVO vo = new TopicPostVO();
        vo.setPostId(post.getId());
        vo.setUserId(post.getUserId());
        vo.setTitle(post.getTitle());
        vo.setContent(post.getContent());
        vo.setUpvoteCount(post.getUpvoteCount() == null ? 0 : post.getUpvoteCount());
        vo.setCommentCount(post.getCommentCount() == null ? 0 : post.getCommentCount());
        vo.setTipTotal(post.getTipTotal() == null ? 0 : post.getTipTotal());
        vo.setTipEnabled(post.getTipEnabled() != null && post.getTipEnabled() == 1);
        vo.setUpvoted(false);
        vo.setCreateTime(post.getCreateTime());
        return vo;
    }
}
