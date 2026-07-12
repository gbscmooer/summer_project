package com.campus.order.dto;

import lombok.Data;

@Data
public class StatusCountPoint {
    private int status;
    private String statusText;
    private long count;
}
