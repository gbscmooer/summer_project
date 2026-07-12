package com.campus.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_topic_tip_receipt")
public class TopicTipReceipt {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_DONE = 1;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long tipperId;
    private Integer amount;
    private String requestId;
    /** 0=pending 1=done */
    private Integer status;
    private LocalDateTime createTime;
}
