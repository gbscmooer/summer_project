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
    /** 0-普通用户 1-管理员 */
    private Integer role;
}
