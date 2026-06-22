package com.campus.order.feign.dto;

import lombok.Data;

/**
 * 用户简要信息 DTO（订单服务侧自定义，避免与用户服务强耦合）。
 * 对应 campus-user 的 {@code GET /user/batch} 返回结构。
 */
@Data
public class UserBriefDTO {
    private Long userId;
    private String nickname;
    private String phone;
}
