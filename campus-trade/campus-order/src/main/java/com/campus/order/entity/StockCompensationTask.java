package com.campus.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_stock_compensation_task")
public class StockCompensationTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String orderNo;
    private Integer status;
    private Integer attempts;
    private LocalDateTime nextRetryTime;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
