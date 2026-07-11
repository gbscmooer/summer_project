package com.campus.product.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiSearchRequest {
    @NotBlank(message = "请描述你想购买的商品")
    @Size(max = 500, message = "购买需求最多500字")
    private String query;

    @Min(value = 1, message = "返回数量至少为1")
    @Max(value = 20, message = "返回数量最多为20")
    private Integer pageSize = 12;
}
