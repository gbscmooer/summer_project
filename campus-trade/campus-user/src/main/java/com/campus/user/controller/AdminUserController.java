package com.campus.user.controller;

import com.campus.common.dto.UserPermissionsVO;
import com.campus.common.exception.BizException;
import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import com.campus.user.dto.AdminUserVO;
import com.campus.user.dto.BanUserRequest;
import com.campus.user.dto.UpdateUserPermissionsRequest;
import com.campus.user.service.AdminAuthService;
import com.campus.user.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminAuthService adminAuthService;
    private final AdminUserService adminUserService;

    @GetMapping("/list")
    public Result<PageResult<AdminUserVO>> listUsers(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        requireAdmin(userId);
        return Result.success(adminUserService.listUsers(pageNum, pageSize, keyword));
    }

    @PostMapping("/{id}/ban")
    public Result<Void> banUser(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id,
            @Valid @RequestBody BanUserRequest request) {
        requireAdmin(userId);
        adminUserService.banUser(userId, id, request);
        return Result.success("用户已封禁", null);
    }

    @PostMapping("/{id}/unban")
    public Result<Void> unbanUser(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id) {
        requireAdmin(userId);
        adminUserService.unbanUser(userId, id);
        return Result.success("用户已解封", null);
    }

    @PostMapping("/{id}/permissions")
    public Result<UserPermissionsVO> updatePermissions(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserPermissionsRequest request) {
        requireAdmin(userId);
        return Result.success("权限已更新", adminUserService.updatePermissions(userId, id, request));
    }

    private void requireAdmin(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        adminAuthService.requireAdmin(userId);
    }
}
