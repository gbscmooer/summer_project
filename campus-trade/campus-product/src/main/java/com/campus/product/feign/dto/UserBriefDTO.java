package com.campus.product.feign.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品服务侧使用的用户简要信息 DTO，字段对应 campus-user 的 /user/batch 返回值。
 */
@Data
public class UserBriefDTO {
    private Long userId;
    private String nickname;
    private String avatar;
    private String bio;
    private BigDecimal avgRating;
    private Integer reviewCount;
}
