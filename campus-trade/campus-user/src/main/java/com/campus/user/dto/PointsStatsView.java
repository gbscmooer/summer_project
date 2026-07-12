package com.campus.user.dto;

import lombok.Data;

import java.util.List;

@Data
public class PointsStatsView {
    /** 当前余额 */
    private Integer points;
    /** 区间内支出绝对值之和 */
    private Integer totalSpent;
    /** 购买商品支出 */
    private Integer spentProducts;
    /** 打赏支出 */
    private Integer spentTips;
    /** 区间内入账之和 */
    private Integer totalEarned;
    /** 签到收入 */
    private Integer earnedCheckin;
    /** 点赞任务收入 */
    private Integer earnedLike;
    /** 商品销售收入 */
    private Integer earnedSales;
    /** 收到打赏 */
    private Integer earnedTips;
    private List<CategoryAmount> pieSpend;
    /** 收入构成：checkin / like / sales / tips / other */
    private List<CategoryAmount> pieEarn;
    private List<DailyPointPoint> daily;
}
