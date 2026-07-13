package com.campus.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_product")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String description;
    /** 所需积分（非货币） */
    private BigDecimal price;
    private String images;
    private String category;
    private Long sellerId;
    private Integer status;   // 0-下架 1-在售 2-已售
    private Integer stock;
    private Integer viewCount;
    /** 1-新手教程专用商品 */
    private Integer isTutorial;
    /** 每用户限购数量 */
    private Integer purchaseLimit;
    /** 0-普通购买 1-秒杀 */
    @TableField("sale_type")
    private Integer saleType;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
