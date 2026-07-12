package com.campus.product.dto;

import lombok.Data;

@Data
public class PublishQuotaVO {
    /** 用户角色：0-个人账户 1-管理员 2-商家 */
    private int role;
    /** 当前有效发布数（未下架） */
    private int used;
    /** 限额；-1 表示不限 */
    private int limit;
    /** 剩余可发布数；-1 表示不限 */
    private int remaining;
    private boolean unlimited;
}
