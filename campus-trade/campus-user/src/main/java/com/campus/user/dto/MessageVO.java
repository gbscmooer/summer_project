package com.campus.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageVO {
    private Long messageId;
    private Long senderId;
    private String content;
    private LocalDateTime createTime;
    private boolean mine;
}
