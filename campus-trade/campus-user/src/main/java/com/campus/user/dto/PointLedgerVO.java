package com.campus.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PointLedgerVO {
    private Long id;
    private Integer delta;
    private Integer balanceAfter;
    private String reason;
    private String reasonLabel;
    private String refType;
    private String refId;
    private LocalDateTime createTime;
    /** earned | product | tip | other */
    private String category;
}
