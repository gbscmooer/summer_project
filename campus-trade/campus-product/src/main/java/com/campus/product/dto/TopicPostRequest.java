package com.campus.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class TopicPostRequest {

    @NotBlank(message = "请填写标题")
    @Size(max = 100, message = "标题最多100字")
    private String title;

    /** 正文可选；支持富文本 HTML，空则存空字符串 */
    @Size(max = 50000, message = "正文过长")
    private String content;

    /** 附带商品 ID，须为本人发布的商品，最多 5 个 */
    @Size(max = 5, message = "最多附带5个商品")
    private List<Long> productIds;

    /** 是否开启打赏，默认 false */
    private Boolean tipEnabled;
}
