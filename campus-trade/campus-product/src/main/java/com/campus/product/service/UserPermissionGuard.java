package com.campus.product.service;

import com.campus.common.dto.UserPermissionsVO;
import com.campus.common.exception.BizException;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import com.campus.product.feign.UserFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 跨服务查询用户细粒度权限并强制校验。
 */
@Service
@RequiredArgsConstructor
public class UserPermissionGuard {

    private final UserFeignClient userFeignClient;

    public void requireCanPost(Long userId) {
        if (!load(userId).isCanPost()) {
            throw new BizException(ResultCode.PERMISSION_DENIED_POST);
        }
    }

    public void requireCanComment(Long userId) {
        if (!load(userId).isCanComment()) {
            throw new BizException(ResultCode.PERMISSION_DENIED_COMMENT);
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
