package com.campus.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserProfileVO {
    private Long userId;
    private String nickname;
    private String avatar;
    private String bio;
    /** 0-个人账户 1-管理员 2-商家 */
    private Integer role;
    private LocalDateTime createTime;
    private Long followingCount;
    private Long followerCount;
    /** 帖子数（跨服务可选，默认 0） */
    private Long postCount;
    /** 当前登录用户是否已关注该主页用户 */
    private Boolean following;
    /** 是否本人主页 */
    private Boolean self;
}
