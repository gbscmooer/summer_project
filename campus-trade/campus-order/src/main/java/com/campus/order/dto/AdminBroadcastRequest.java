package com.campus.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AdminBroadcastRequest {

    @NotBlank
    @Size(max = 100)
    private String title;

    @NotBlank
    @Size(max = 500)
    private String content;

    /** ALL = 全员；SPECIFIC = 指定用户 */
    @NotBlank
    private String targetType;

    /** 指定用户 ID，targetType=SPECIFIC 时与 usernames 二选一或组合使用 */
    private List<Long> userIds;

    /** 指定用户名（逗号分隔逻辑在后端解析），targetType=SPECIFIC 时使用 */
    private List<String> usernames;
}
