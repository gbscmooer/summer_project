package com.campus.product.dto;

import lombok.Data;

import java.util.List;

@Data
public class UserActivityHeatmapView {
    private long totalCount;
    private List<DailyActivityPoint> days;
    private String mostActiveMonth;
    private String mostActiveDay;
    private int longestStreak;
    private int currentStreak;
}
