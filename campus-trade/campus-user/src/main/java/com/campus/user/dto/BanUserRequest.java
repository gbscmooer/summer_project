package com.campus.user.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BanUserRequest {

    @NotBlank(message = "请填写封禁原因")
    @Size(max = 500, message = "封禁原因最多500字")
    private String reason;

    /** 封禁天数，0 或 null 表示永久封禁 */
    @Min(value = 0, message = "封禁天数不能为负数")
    private Integer durationDays;
}
