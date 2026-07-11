package com.campus.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_stock_restore_log")
public class StockRestoreLogEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String orderNo;
    private LocalDateTime createTime;
}
