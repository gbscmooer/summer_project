package com.campus.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FollowUserVO {
    private Long userId;
    private String nickname;
    private String avatar;
    private String bio;
    private LocalDateTime followTime;
}
