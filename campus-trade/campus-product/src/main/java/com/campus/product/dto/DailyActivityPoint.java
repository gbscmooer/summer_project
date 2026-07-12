package com.campus.product.dto;

import lombok.Data;

@Data
public class DailyActivityPoint {
    private String date;
    private int count;
    private int postCount;
    private int commentCount;
}
