package com.campus.user.dto;

import com.campus.user.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PublicUserProfileVO {
    private Long userId;
    private String nickname;
    private String bio;
    private String avatar;
    private String coverImage;
    private String ipLocation;
    /** 0-个人账户 1-管理员 2-商家 */
    private Integer role;
    private LocalDateTime createTime;
    private int followingCount;
    private int followersCount;
    /** 当前登录用户是否已关注该用户 */
    private boolean following;

    public static PublicUserProfileVO from(User user, int followingCount, int followersCount, boolean following) {
        PublicUserProfileVO vo = new PublicUserProfileVO();
        vo.setUserId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setBio(user.getBio());
        vo.setAvatar(user.getAvatar());
        vo.setCoverImage(user.getCoverImage());
        vo.setIpLocation(user.getIpLocation());
        vo.setRole(user.getRole() == null ? 0 : user.getRole());
        vo.setCreateTime(user.getCreateTime());
        vo.setFollowingCount(followingCount);
        vo.setFollowersCount(followersCount);
        vo.setFollowing(following);
        return vo;
    }
}
