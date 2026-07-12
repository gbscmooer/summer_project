package com.campus.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FriendVO {
    private Long userId;
    private String nickname;
    private String avatar;
    private LocalDateTime friendsSince;
}
