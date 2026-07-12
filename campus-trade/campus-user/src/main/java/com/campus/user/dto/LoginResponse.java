package com.campus.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private Long userId;
    private String nickname;
    private String avatar;
    /** 0-个人账户 1-管理员 2-商家 */
    private Integer role;
    /** 1-已完成新手教程 */
    private Integer onboardingCompleted;
    /** 积分余额 */
    private Integer points;
}
