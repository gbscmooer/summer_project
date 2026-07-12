package com.campus.user.controller;

import com.campus.common.exception.BizException;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import com.campus.common.security.InternalApiTokenValidator;
import com.campus.user.dto.*;
import com.campus.user.service.PasswordResetService;
import com.campus.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final PasswordResetService passwordResetService;
    private final InternalApiTokenValidator internalApiTokenValidator;

    @PostMapping("/register")
    public Result<Map<String, Long>> register(@Valid @RequestBody RegisterRequest request) {
        Long userId = userService.register(request);
        return Result.success("注册成功", Map.of("userId", userId));
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return Result.success("登录成功", response);
    }

    /** 忘记密码：按用户名发重置邮件（防枚举，统一成功文案）。 */
    @PostMapping("/forgot-password")
    public Result<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.getUsername());
        return Result.success("如果该账号已绑定邮箱，重置链接已发送，请查收邮件", null);
    }

    /** 通过邮件链接中的 token 设置新密码。 */
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return Result.success("密码已重置，请使用新密码登录", null);
    }

    // X-User-Id 由网关解析JWT后注入；本地测试时手动传该请求头
    @GetMapping("/info")
    public Result<UserInfoResponse> getUserInfo(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return Result.success(userService.getUserInfo(userId));
    }

    @PostMapping("/info")
    public Result<Void> updateUserInfo(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody UpdateUserRequest request) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        userService.updateUserInfo(userId, request);
        return Result.success("更新成功", null);
    }

    // 内部接口，供 OpenFeign 调用，不经过网关
    @GetMapping("/internal/role")
    public Result<Integer> getUserRole(
            @RequestHeader(value = InternalApiTokenValidator.HEADER_NAME, required = false) String internalToken,
            @RequestParam Long userId) {
        internalApiTokenValidator.requireValid(internalToken);
        return Result.success(userService.getRole(userId));
    }

    @GetMapping("/batch")
    public Result<List<UserBriefVO>> batchGetUsers(
            @RequestHeader(value = InternalApiTokenValidator.HEADER_NAME, required = false) String internalToken,
            @RequestParam String ids) {
        internalApiTokenValidator.requireValid(internalToken);
        List<Long> idList = new ArrayList<>();
        for (String part : ids.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                idList.add(Long.parseLong(trimmed));
            } catch (NumberFormatException e) {
                throw new BizException(ResultCode.BAD_REQUEST);
            }
        }
        if (idList.size() > 100) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "单次最多查询100个用户");
        }
        return Result.success(userService.batchGetUsers(idList));
    }

    @GetMapping("/internal/all-ids")
    public Result<List<Long>> listAllUserIds(
            @RequestHeader(value = InternalApiTokenValidator.HEADER_NAME, required = false) String internalToken) {
        internalApiTokenValidator.requireValid(internalToken);
        return Result.success(userService.listAllUserIds());
    }

    @GetMapping("/internal/resolve-usernames")
    public Result<List<Long>> resolveUserIdsByUsernames(
            @RequestHeader(value = InternalApiTokenValidator.HEADER_NAME, required = false) String internalToken,
            @RequestParam String usernames) {
        internalApiTokenValidator.requireValid(internalToken);
        List<String> names = new ArrayList<>();
        for (String part : usernames.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                names.add(trimmed);
            }
        }
        if (names.size() > 200) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "单次最多指定200个用户名");
        }
        return Result.success(userService.resolveUserIdsByUsernames(names));
    }

    @GetMapping("/onboarding")
    public Result<OnboardingStatusResponse> getOnboardingStatus(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return Result.success(userService.getOnboardingStatus(userId));
    }

    @PostMapping("/onboarding/step")
    public Result<Void> markOnboardingStep(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody MarkOnboardingStepRequest request) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        userService.markOnboardingStep(userId, request.getStep());
        return Result.success("已记录", null);
    }

    @PostMapping("/onboarding/complete")
    public Result<Void> completeOnboarding(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        userService.completeOnboarding(userId);
        return Result.success("新手教程已完成", null);
    }
}
