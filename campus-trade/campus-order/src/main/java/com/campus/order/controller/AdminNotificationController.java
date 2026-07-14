package com.campus.order.controller;

import com.campus.common.result.Result;
import com.campus.order.dto.AdminBroadcastRequest;
import com.campus.order.dto.AdminBroadcastResult;
import com.campus.order.service.AdminAuthService;
import com.campus.order.service.NotificationService;
import com.campus.order.service.UserPermissionGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/order/admin/notification")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final AdminAuthService adminAuthService;
    private final NotificationService notificationService;
    private final UserPermissionGuard userPermissionGuard;

    @PostMapping("/broadcast")
    public Result<AdminBroadcastResult> broadcast(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody AdminBroadcastRequest request) {
        adminAuthService.requireNotificationSender(userId);
        userPermissionGuard.requireCanBroadcast(userId);
        AdminBroadcastResult result = notificationService.broadcast(userId, request);
        return Result.success("通知已发送", result);
    }
}
