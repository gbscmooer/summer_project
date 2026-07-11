package com.campus.product.ai.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AiListingDraftRequest {
    @NotEmpty(message = "请至少上传一张商品图片")
    @Size(max = 5, message = "最多上传5张商品图片")
    private List<String> images;

    @Size(max = 500, message = "补充说明最多500字")
    private String notes;
}
