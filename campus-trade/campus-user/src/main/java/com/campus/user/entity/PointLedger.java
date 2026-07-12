package com.campus.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_point_ledger")
public class PointLedger {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer delta;
    private Integer balanceAfter;
    private String reason;
    private String refType;
    private String refId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
