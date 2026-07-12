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

    private static final int ROLE_ADMIN = UserRole.ADMIN;

    private final UserFeignClient userFeignClient;

    public void requireAdmin(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        Result<Integer> result = userFeignClient.getUserRole(userId);
        if (result == null || result.getCode() != ResultCode.SUCCESS.getCode() || result.getData() == null) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        if (result.getData() != ROLE_ADMIN) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
    }
}
