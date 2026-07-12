package com.campus.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PeerUserRequest {
    @NotNull(message = "peerUserId 不能为空")
    private Long peerUserId;
}
