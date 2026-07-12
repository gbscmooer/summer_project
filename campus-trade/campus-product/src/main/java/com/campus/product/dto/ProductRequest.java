package com.campus.product.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductRequest {
    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题最多100字")
    private String title;

    private String description;

    @NotNull(message = "积分不能为空")
    @DecimalMin(value = "1", message = "积分必须大于等于1")
    private BigDecimal price;

    private String images;
    private String category;

    @Min(value = 1, message = "库存至少为1")
    private Integer stock = 1;
}
