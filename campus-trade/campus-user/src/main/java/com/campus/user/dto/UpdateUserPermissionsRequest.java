package com.campus.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员更新用户细粒度权限。
 */
@Data
public class UpdateUserPermissionsRequest {
    @NotNull(message = "canPost 不能为空")
    private Boolean canPost;
    @NotNull(message = "canComment 不能为空")
    private Boolean canComment;
    @NotNull(message = "canOrder 不能为空")
    private Boolean canOrder;
    @NotNull(message = "canBroadcast 不能为空")
    private Boolean canBroadcast;
}
