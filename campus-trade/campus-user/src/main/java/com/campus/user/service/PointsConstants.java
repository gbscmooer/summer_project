package com.campus.user.service;

/** 积分活动常量。 */
public final class PointsConstants {

    public static final int NEW_USER_BONUS = 100;
    public static final int CHECKIN_REWARD = 10;
    public static final int LIKE_QUEST_TARGET = 5;
    public static final int LIKE_QUEST_REWARD = 20;

    public static final String REASON_CHECKIN = "DAILY_CHECKIN";
    public static final String REASON_LIKE_REWARD = "DAILY_LIKE_REWARD";
    public static final String REASON_NEW_USER_BONUS = "NEW_USER_BONUS";
    public static final String REASON_ORDER_PAY_DEBIT = "ORDER_PAY_DEBIT";
    public static final String REASON_ORDER_PAY_CREDIT = "ORDER_PAY_CREDIT";
    public static final String REASON_TOPIC_TIP_DEBIT = "TOPIC_TIP_DEBIT";
    public static final String REASON_TOPIC_TIP_CREDIT = "TOPIC_TIP_CREDIT";

    public static final String REF_CHECKIN = "CHECKIN";
    public static final String REF_LIKE_QUEST = "LIKE_QUEST";
    public static final String REF_REGISTER = "REGISTER";
    public static final String REF_ORDER = "ORDER";
    public static final String REF_TOPIC_TIP = "TOPIC_TIP";

    private PointsConstants() {
    }
}
