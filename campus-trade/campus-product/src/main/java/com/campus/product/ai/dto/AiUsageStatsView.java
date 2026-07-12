package com.campus.product.ai.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiUsageStatsView {
    private long dailyUserLimit;
    private long globalConcurrencyLimit;
    private long globalInflight;
    private long todayTotalRequests;
    private long todayActiveUsers;
    private String usageDate;
}
