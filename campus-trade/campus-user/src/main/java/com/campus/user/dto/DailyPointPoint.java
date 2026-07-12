package com.campus.user.dto;

import lombok.Data;

@Data
public class DailyPointPoint {
    /** YYYY-MM-DD */
    private String date;
    private Integer spentProducts;
    private Integer spentTips;
    /** 当日收入合计 */
    private Integer earned;
    private Integer earnedCheckin;
    private Integer earnedLike;
    private Integer earnedSales;
    private Integer earnedTips;
}
