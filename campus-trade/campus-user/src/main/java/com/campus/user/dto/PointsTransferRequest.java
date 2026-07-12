package com.campus.user.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PointsTransferRequest {
    @NotNull(message = "fromUserId 不能为空")
    private Long fromUserId;

    @NotNull(message = "toUserId 不能为空")
    private Long toUserId;

    @NotNull(message = "amount 不能为空")
    @Min(value = 1, message = "amount 至少为 1")
    private Integer amount;

    /** 业务原因，如 ORDER_PAY */
    private String reason;

    /** 关联类型，如 ORDER */
    private String refType;

    /** 关联业务 ID，如订单 ID */
    private String refId;
}
