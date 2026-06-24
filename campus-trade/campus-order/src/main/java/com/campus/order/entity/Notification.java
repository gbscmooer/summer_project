package com.campus.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知实体，对应 {@code t_notification} 表。
 *
 * <p>由 MQ 消费者在收到下单消息后写入，前端轮询查询展示给卖家。
 */
@Data
@TableName("t_notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收人 userId（卖家）。 */
    private Long userId;

    /** 通知类型：ORDER_CREATED / ORDER_PAID / ... 便于扩展。 */
    private String type;

    /** 通知标题。 */
    private String title;

    /** 通知内容。 */
    private String content;

    /** 关联订单号（可为空，方便点击跳转）。 */
    private String orderNo;

    /** 是否已读：0-未读 1-已读。 */
    private Integer isRead;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
