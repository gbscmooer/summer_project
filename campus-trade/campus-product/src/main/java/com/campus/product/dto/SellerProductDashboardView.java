package com.campus.product.dto;

import lombok.Data;

import java.util.List;

@Data
public class SellerProductDashboardView {
    private SellerProductStatsView summary;
    private List<CategoryCountPoint> categoryBreakdown;
}
