package com.campus.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 评价信誉增量幂等日志：同一 reviewId 只加分一次。 */
@Data
@TableName("t_rating_apply_log")
public class RatingApplyLog {

    @TableId(type = IdType.INPUT)
    private Long reviewId;

    private Long sellerId;

    private Integer rating;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
