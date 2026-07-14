package com.campus.user.controller;

import com.campus.common.exception.BizException;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import com.campus.user.dto.SpecialCertApplicationRequest;
import com.campus.user.dto.SpecialCertApplicationVO;
import com.campus.user.dto.SpecialCertReviewRequest;
import com.campus.user.service.AdminAuthService;
import com.campus.user.service.SpecialCertApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/special-cert")
@RequiredArgsConstructor
public class SpecialCertController {

    private final SpecialCertApplicationService specialCertApplicationService;

    @PostMapping("/apply")
    public Result<Void> apply(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody SpecialCertApplicationRequest request) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        specialCertApplicationService.apply(userId, request);
        return Result.success("申请已提交，请等待管理员审核", null);
    }

    @GetMapping("/application")
    public Result<SpecialCertApplicationVO> myApplication(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return Result.success(specialCertApplicationService.getMyApplication(userId));
    }
}

@RestController
@RequestMapping("/user/admin/special-cert")
@RequiredArgsConstructor
class AdminSpecialCertController {

    private final AdminAuthService adminAuthService;
    private final SpecialCertApplicationService specialCertApplicationService;

    @GetMapping("/applications")
    public Result<List<SpecialCertApplicationVO>> listPending(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        adminAuthService.requireAdmin(userId);
        return Result.success(specialCertApplicationService.listPending());
    }

    @PostMapping("/applications/{id}/approve")
    public Result<Void> approve(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id,
            @RequestBody(required = false) SpecialCertReviewRequest request) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        adminAuthService.requireAdmin(userId);
        String note = request != null ? request.getAdminNote() : null;
        specialCertApplicationService.approve(userId, id, note);
        return Result.success("已通过，用户已升级为特殊认证", null);
    }

    @PostMapping("/applications/{id}/reject")
    public Result<Void> reject(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id,
            @RequestBody(required = false) SpecialCertReviewRequest request) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        adminAuthService.requireAdmin(userId);
        String note = request != null ? request.getAdminNote() : null;
        specialCertApplicationService.reject(userId, id, note);
        return Result.success("已拒绝该申请", null);
    }
}
