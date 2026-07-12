package com.campus.product.dto;

import lombok.Data;

@Data
public class PublishQuotaVO {
    /** 用户角色：0-个人账户 1-管理员 2-商家 */
    private int role;
    /** 当前有效发布数（在售） */
    private int used;
    /** 限额；-1 表示商家/管理员不限；普通用户为 0 */
    private int limit;
    /** 剩余可发布数；-1 表示不限；普通用户为 0 */
    private int remaining;
    /** 是否可无限发布（商家或管理员） */
    private boolean unlimited;
}
