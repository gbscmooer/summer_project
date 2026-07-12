package com.campus.product.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_topic_post_product")
public class TopicPostProduct {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;

    private Long productId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
