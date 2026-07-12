package com.campus.user.dto;

import lombok.Data;

@Data
public class FriendStatusVO {
    private boolean friends;
    private boolean pendingOutgoing;
    private boolean pendingIncoming;
    private boolean tradeUnlocked;
    private boolean canMessageUnlimited;
}
