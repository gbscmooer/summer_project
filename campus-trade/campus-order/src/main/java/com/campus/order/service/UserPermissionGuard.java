package com.campus.order.service;

import com.campus.common.dto.UserPermissionsVO;
import com.campus.common.exception.BizException;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import com.campus.order.feign.UserFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 跨服务查询用户细粒度权限并强制校验。
 */
@Service
@RequiredArgsConstructor
public class UserPermissionGuard {

    private final UserFeignClient userFeignClient;

    public void requireCanOrder(Long userId) {
        if (!load(userId).isCanOrder()) {
            throw new BizException(ResultCode.PERMISSION_DENIED_ORDER);
        }
    }

    public void requireCanBroadcast(Long userId) {
        if (!load(userId).isCanBroadcast()) {
            throw new BizException(ResultCode.PERMISSION_DENIED_BROADCAST);
        }
    }

    private UserPermissionsVO load(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        Result<UserPermissionsVO> result = userFeignClient.getPermissions(userId);
        if (result == null || result.getCode() != ResultCode.SUCCESS.getCode() || result.getData() == null) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return result.getData();
    }
}
