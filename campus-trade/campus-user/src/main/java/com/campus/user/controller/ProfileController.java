package com.campus.user.controller;

import com.campus.common.exception.BizException;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import com.campus.user.dto.PublicUserProfileVO;
import com.campus.user.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/{userId}")
    public Result<PublicUserProfileVO> getProfile(
            @PathVariable Long userId,
            @RequestHeader(value = "X-User-Id", required = false) Long viewerUserId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        return Result.success(profileService.getPublicProfile(userId, viewerUserId));
    }
}
