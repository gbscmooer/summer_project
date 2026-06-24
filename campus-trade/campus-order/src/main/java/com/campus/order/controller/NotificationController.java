package com.campus.order.controller;

import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.order.dto.NotificationVO;
import com.campus.order.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 通知接口（挂在 order 服务下，路由 /order/notification/**）。
 */
@RestController
@RequestMapping("/order/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** 我的通知列表（分页，最新在前）。 */
    @GetMapping("/list")
    public Result<PageResult<NotificationVO>> list(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(notificationService.listByUser(userId, pageNum, pageSize));
    }

    /** 未读数量（用于导航栏小红点）。 */
    @GetMapping("/unread-count")
    public Result<Long> unreadCount(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(notificationService.unreadCount(userId));
    }

    /** 标记单条已读。 */
    @PutMapping("/{id}/read")
    public Result<Void> markRead(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        notificationService.markRead(userId, id);
        return Result.success("已标记已读", null);
    }

    /** 一键全部已读。 */
    @PutMapping("/read-all")
    public Result<Void> markAllRead(@RequestHeader("X-User-Id") Long userId) {
        notificationService.markAllRead(userId);
        return Result.success("已全部标记已读", null);
    }
}
