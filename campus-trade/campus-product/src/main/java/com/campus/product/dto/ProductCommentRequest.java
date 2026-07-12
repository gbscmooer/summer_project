package com.campus.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductCommentRequest {

    @NotBlank(message = "请填写留言内容")
    @Size(max = 500, message = "留言内容最多500字")
    private String content;
}
