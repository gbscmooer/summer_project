package com.campus.order.dto;

import lombok.Data;

import java.util.List;

@Data
public class SellerDashboardView {
    private SellerIncomeStatsView summary;
    private List<DailyRevenuePoint> dailyRevenue;
    private List<StatusCountPoint> orderStatusBreakdown;
}
