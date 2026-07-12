package com.campus.product.dto;

import lombok.Data;

@Data
public class SellerProductStatsView {
    private int activeListings;
    private int soldListings;
    private long totalViews;
}
