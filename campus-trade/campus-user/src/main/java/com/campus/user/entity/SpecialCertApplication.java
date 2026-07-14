package com.campus.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_special_cert_application")
public class SpecialCertApplication {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** 认证展示名称，例如「校园集市官方」 */
    private String displayName;
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
