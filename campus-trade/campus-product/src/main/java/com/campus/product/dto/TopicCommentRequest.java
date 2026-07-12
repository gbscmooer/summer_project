package com.campus.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TopicCommentRequest {

    /** 父评论 ID；为空表示顶层评论 */
    private Long parentId;

    @NotBlank(message = "请填写评论内容")
    @Size(max = 1000, message = "评论最多1000字")
    private String content;

    /** 可选配图，须为本站已上传的 /api/product/image/... URL */
    @Size(max = 255, message = "图片地址过长")
    private String imageUrl;
}
