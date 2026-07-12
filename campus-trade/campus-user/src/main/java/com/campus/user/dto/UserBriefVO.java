package com.campus.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserBriefVO {
    private Long userId;
    private String nickname;
    private String avatar;
    private String bio;
    private BigDecimal avgRating;
    private Integer reviewCount;
}
