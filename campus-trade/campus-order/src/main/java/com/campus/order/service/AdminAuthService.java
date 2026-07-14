package com.campus.order.service;

import com.campus.common.constant.UserRole;
import com.campus.common.exception.BizException;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import com.campus.order.feign.UserFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final UserFeignClient userFeignClient;

    public void requireAdmin(Long userId) {
        int role = resolveRole(userId);
        if (!UserRole.isAdmin(role)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
    }

    /** 管理员或特殊认证官方可发送系统通知。 */
    public void requireNotificationSender(Long userId) {
        int role = resolveRole(userId);
        if (!UserRole.canSendNotification(role)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
    }

    private int resolveRole(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        Result<Integer> result = userFeignClient.getUserRole(userId);
        if (result == null || result.getCode() != ResultCode.SUCCESS.getCode() || result.getData() == null) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return result.getData();
    }
}
