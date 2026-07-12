package com.campus.product.controller;

import com.campus.common.result.Result;
import com.campus.product.ai.dto.AiAdminConfigRequest;
import com.campus.product.ai.dto.AiAdminConfigResponse;
import com.campus.product.ai.dto.AiHealthStatusView;
import com.campus.product.ai.service.AiSettingsService;
import com.campus.product.service.AdminAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/ai-config")
@RequiredArgsConstructor
public class AdminAiConfigController {

    private final AdminAuthService adminAuthService;
    private final AiSettingsService aiSettingsService;

    @GetMapping
    public Result<AiAdminConfigResponse> get(@RequestHeader("X-User-Id") Long userId) {
        adminAuthService.requireAdmin(userId);
        return Result.success(aiSettingsService.getAdminView());
    }

    @PostMapping
    public Result<AiAdminConfigResponse> save(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody AiAdminConfigRequest request) {
        adminAuthService.requireAdmin(userId);
        return Result.success("AI 配置已保存", aiSettingsService.saveAdminConfig(userId, request));
    }

    @PostMapping("/probe")
    public Result<AiHealthStatusView> probe(
            @RequestHeader("X-User-Id") Long userId) {
        adminAuthService.requireAdmin(userId);
        return Result.success(aiSettingsService.probeHealth());
    }
}
