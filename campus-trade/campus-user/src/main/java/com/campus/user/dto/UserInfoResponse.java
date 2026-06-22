package com.campus.user.dto;

import com.campus.user.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserInfoResponse {
    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
    private String phone;
    private LocalDateTime createTime;

    public static UserInfoResponse from(User user) {
        UserInfoResponse vo = new UserInfoResponse();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setPhone(user.getPhone());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
