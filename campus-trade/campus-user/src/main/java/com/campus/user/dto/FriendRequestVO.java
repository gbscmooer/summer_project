package com.campus.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FriendRequestVO {
    private Long id;
    private Long fromUserId;
    private String nickname;
    private String avatar;
    private Integer status;
    private LocalDateTime createTime;
}
