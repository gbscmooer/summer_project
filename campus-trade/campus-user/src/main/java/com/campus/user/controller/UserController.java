package com.campus.user.controller;

import com.campus.common.result.Result;
import com.campus.user.dto.RegisterRequest;
import com.campus.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
}
