package com.campus.user.dto;

import lombok.Data;

@Data
public class EventStatusVO {
    /** 今日是否已签到 */
    private boolean checkedInToday;
    /** 今日点赞次数（话题帖 upvote 成功计入，取消不扣） */
    private int likeCountToday;
    /** 每日点赞目标次数 */
    private int likeTarget;
    /** 今日点赞奖励是否已领取 */
    private boolean likeRewardClaimed;
    /** 是否可领取点赞奖励（满目标且未领） */
    private boolean likeRewardClaimable;
    /** 签到奖励积分（展示用） */
    private int checkinPoints;
    /** 点赞任务奖励积分（展示用） */
    private int likeRewardPoints;
    /** 当前积分余额 */
    private Integer points;

    /** 前端兼容别名：likeCount */
    public int getLikeCount() {
        return likeCountToday;
    }

    /** 前端兼容别名：likeRewarded */
    public boolean isLikeRewarded() {
        return likeRewardClaimed;
    }
}
