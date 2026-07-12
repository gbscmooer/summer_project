package com.campus.user.service;

import com.campus.common.constant.UserRole;
import com.campus.common.exception.BizException;
import com.campus.common.result.ResultCode;
import com.campus.user.entity.User;
import com.campus.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final UserMapper userMapper;

    public void requireAdmin(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        User user = userMapper.selectById(userId);
        if (user == null || user.getRole() == null || user.getRole() != UserRole.ADMIN) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
    }
}
