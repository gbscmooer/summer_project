package com.campus.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_friendship")
public class Friendship {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userLowId;
    private Long userHighId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
