package com.campus.user.controller;

import com.campus.common.result.Result;
import com.campus.user.dto.*;
import com.campus.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

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

    // X-User-Id 由网关解析JWT后注入；本地测试时手动传该请求头
    @GetMapping("/info")
    public Result<UserInfoResponse> getUserInfo(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(userService.getUserInfo(userId));
    }

    @PutMapping("/info")
    public Result<Void> updateUserInfo(@RequestHeader("X-User-Id") Long userId,
                                       @RequestBody UpdateUserRequest request) {
        userService.updateUserInfo(userId, request);
        return Result.success("更新成功", null);
    }

    // 内部接口，供 OpenFeign 调用，不经过网关
    @GetMapping("/batch")
    public Result<List<UserBriefVO>> batchGetUsers(@RequestParam String ids) {
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());
        return Result.success(userService.batchGetUsers(idList));
    }
}
