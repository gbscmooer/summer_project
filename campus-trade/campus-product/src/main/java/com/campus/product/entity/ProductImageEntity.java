package com.campus.product.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_product_image")
public class ProductImageEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String filename;
    private Long uploaderId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
