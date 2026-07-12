package com.campus.product.feign.dto;

import lombok.Data;

@Data
public class PointsTransferRequest {
    private Long fromUserId;
    private Long toUserId;
    private Integer amount;
    private String reason;
    private String refType;
    private String refId;
}
