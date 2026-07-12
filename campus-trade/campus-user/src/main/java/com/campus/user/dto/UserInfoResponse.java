package com.campus.user.dto;

import com.campus.user.entity.User;
import com.campus.user.service.OnboardingFlagCodec;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class UserInfoResponse {
    private Long userId;
    private String username;
    private String nickname;
    private String bio;
    private String avatar;
    private String coverImage;
    private String ipLocation;
    private String phone;
    private String email;
    private Integer emailVerified;
    /** 0-个人账户 1-管理员 2-商家 */
    private Integer role;
    private LocalDateTime createTime;
    /** 1-已完成新手教程 */
    private Integer onboardingCompleted;
    /** 步骤标记，如 browse / ai / notify / profile */
    private Map<String, Boolean> flags;
    /** 积分余额 */
    private Integer points;
    /** 卖家平均评分 */
    private BigDecimal avgRating;
    /** 卖家收到的评价数 */
    private Integer reviewCount;

    public static UserInfoResponse from(User user) {
        UserInfoResponse vo = new UserInfoResponse();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setBio(user.getBio());
        vo.setAvatar(user.getAvatar());
        vo.setCoverImage(user.getCoverImage());
        vo.setIpLocation(user.getIpLocation());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setEmailVerified(user.getEmailVerified() == null ? 0 : user.getEmailVerified());
        vo.setRole(user.getRole() == null ? 0 : user.getRole());
        vo.setCreateTime(user.getCreateTime());
        vo.setOnboardingCompleted(user.getOnboardingCompleted() == null ? 0 : user.getOnboardingCompleted());
        vo.setFlags(OnboardingFlagCodec.decode(user.getOnboardingFlags()));
        vo.setPoints(user.getPoints() == null ? 0 : user.getPoints());
        vo.setAvgRating(user.getAvgRating());
        vo.setReviewCount(user.getReviewCount() == null ? 0 : user.getReviewCount());
        return vo;
    }
}
