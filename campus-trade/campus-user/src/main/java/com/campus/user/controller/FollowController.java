package com.campus.user.controller;

import com.campus.common.exception.BizException;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import com.campus.user.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/user/follow")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @PostMapping("/{userId}")
    public Result<Void> follow(
            @RequestHeader(value = "X-User-Id", required = false) Long followerId,
            @PathVariable Long userId) {
        requireLogin(followerId);
        followService.follow(followerId, userId);
        return Result.success("已关注", null);
    }

    @PostMapping("/{userId}/unfollow")
    public Result<Void> unfollow(
            @RequestHeader(value = "X-User-Id", required = false) Long followerId,
            @PathVariable Long userId) {
        requireLogin(followerId);
        followService.unfollow(followerId, userId);
        return Result.success("已取消关注", null);
    }

    @GetMapping("/following")
    public Result<Map<String, Integer>> followingCount(@RequestParam Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        return Result.success(Map.of("count", followService.countFollowing(userId)));
    }

    @GetMapping("/followers")
    public Result<Map<String, Integer>> followersCount(@RequestParam Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        return Result.success(Map.of("count", followService.countFollowers(userId)));
    }

    private void requireLogin(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
    }
}
