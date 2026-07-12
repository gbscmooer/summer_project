package com.campus.user.dto;

import com.campus.user.entity.User;
import com.campus.user.service.OnboardingFlagCodec;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class UserInfoResponse {
    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
    private String phone;
    /** 0-个人账户 1-管理员 2-商家 */
    private Integer role;
    private LocalDateTime createTime;
    /** 1-已完成新手教程 */
    private Integer onboardingCompleted;
    /** 步骤标记，如 browse / ai / notify / profile */
    private Map<String, Boolean> flags;

    public static UserInfoResponse from(User user) {
        UserInfoResponse vo = new UserInfoResponse();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setPhone(user.getPhone());
        vo.setRole(user.getRole() == null ? 0 : user.getRole());
        vo.setCreateTime(user.getCreateTime());
        vo.setOnboardingCompleted(user.getOnboardingCompleted() == null ? 0 : user.getOnboardingCompleted());
        vo.setFlags(OnboardingFlagCodec.decode(user.getOnboardingFlags()));
        return vo;
    }
}
