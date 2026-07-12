package com.campus.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationVO {
    private Long conversationId;
    private Long peerUserId;
    private String peerNickname;
    private String peerAvatar;
    private String lastMsgPreview;
    private LocalDateTime lastMessageAt;
    private long unreadCount;
    private boolean canMessageUnlimited;
    private boolean friends;
}
