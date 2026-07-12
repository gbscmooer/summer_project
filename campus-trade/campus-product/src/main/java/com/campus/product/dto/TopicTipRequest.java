package com.campus.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TopicTipRequest {

    @NotNull(message = "打赏积分不能为空")
    @Min(value = 1, message = "打赏积分须为正整数")
    @Max(value = 10000, message = "单次打赏不超过10000")
    private Integer amount;

    /** 客户端幂等键；同一 requestId 重试不会重复扣款。 */
    @Size(max = 64, message = "requestId 过长")
    private String requestId;
}
