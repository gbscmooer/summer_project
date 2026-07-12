package com.campus.product.controller;

import com.campus.common.result.Result;
import com.campus.product.dto.UserActivityHeatmapView;
import com.campus.product.service.UserActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/product/activity")
@RequiredArgsConstructor
public class UserActivityController {

    private final UserActivityService userActivityService;

    /** 用户发帖/回复活跃度热力图（最近 365 天）。filter: all | posts | comments */
    @GetMapping("/heatmap")
    public Result<UserActivityHeatmapView> heatmap(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "all") String filter) {
        return Result.success(userActivityService.getHeatmap(userId, filter));
    }
}
