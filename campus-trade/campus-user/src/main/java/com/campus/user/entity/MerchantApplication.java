package com.campus.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_merchant_application")
public class MerchantApplication {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String shopName;
    private String reason;
    private String contactPhone;
    /** 0-待审核 1-已通过 2-已拒绝 */
    private Integer status;
    private Long adminId;
    private String adminNote;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
