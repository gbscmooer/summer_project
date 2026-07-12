package com.campus.order.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminBroadcastResult {
    private int recipientCount;
    private String targetType;
}
