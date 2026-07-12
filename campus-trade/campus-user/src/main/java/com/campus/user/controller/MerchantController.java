package com.campus.user.controller;

import com.campus.common.exception.BizException;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import com.campus.user.dto.MerchantApplicationRequest;
import com.campus.user.dto.MerchantApplicationVO;
import com.campus.user.dto.MerchantReviewRequest;
import com.campus.user.service.AdminAuthService;
import com.campus.user.service.MerchantApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/merchant")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantApplicationService merchantApplicationService;

    @PostMapping("/apply")
    public Result<Void> apply(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody MerchantApplicationRequest request) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        merchantApplicationService.apply(userId, request);
        return Result.success("申请已提交，请等待管理员审核", null);
    }

    @GetMapping("/application")
    public Result<MerchantApplicationVO> myApplication(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return Result.success(merchantApplicationService.getMyApplication(userId));
    }
}

@RestController
@RequestMapping("/user/admin/merchant")
@RequiredArgsConstructor
class AdminMerchantController {

    private final AdminAuthService adminAuthService;
    private final MerchantApplicationService merchantApplicationService;

    @GetMapping("/applications")
    public Result<List<MerchantApplicationVO>> listPending(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        adminAuthService.requireAdmin(userId);
        return Result.success(merchantApplicationService.listPending());
    }

    @PostMapping("/applications/{id}/approve")
    public Result<Void> approve(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id,
            @RequestBody(required = false) MerchantReviewRequest request) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        adminAuthService.requireAdmin(userId);
        String note = request != null ? request.getAdminNote() : null;
        merchantApplicationService.approve(userId, id, note);
        return Result.success("已通过，用户已升级为商家", null);
    }

    @PostMapping("/applications/{id}/reject")
    public Result<Void> reject(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id,
            @RequestBody(required = false) MerchantReviewRequest request) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        adminAuthService.requireAdmin(userId);
        String note = request != null ? request.getAdminNote() : null;
        merchantApplicationService.reject(userId, id, note);
        return Result.success("已拒绝该申请", null);
    }
}
