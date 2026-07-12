package com.campus.order.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DailyRevenuePoint {
    private String date;
    private BigDecimal revenue;
    private int orderCount;
}
