package com.campus.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrderReviewRequest {

    @NotNull(message = "请选择评分")
    @Min(value = 1, message = "评分须为1-5")
    @Max(value = 5, message = "评分须为1-5")
    private Integer rating;

    @Size(max = 500, message = "评价内容最多500字")
    private String content;
}
