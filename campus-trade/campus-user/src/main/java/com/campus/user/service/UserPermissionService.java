package com.campus.user.service;

import com.campus.common.constant.UserCapability;
import com.campus.common.dto.UserPermissionsVO;
import com.campus.common.exception.BizException;
import com.campus.common.result.ResultCode;
import com.campus.user.entity.User;
import com.campus.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户细粒度权限查询与强制校验（本服务内直接读库）。
 */
@Service
@RequiredArgsConstructor
public class UserPermissionService {

    private final UserMapper userMapper;

    public UserPermissionsVO getPermissions(Long userId) {
        User user = requireUser(userId);
        return UserPermissionsVO.fromFlags(
                user.getPermPost(),
                user.getPermComment(),
                user.getPermOrder(),
                user.getPermBroadcast());
    }

    public void requireCanPost(Long userId) {
        if (!UserCapability.isAllowed(requireUser(userId).getPermPost())) {
            throw new BizException(ResultCode.PERMISSION_DENIED_POST);
        }
    }

    public void requireCanComment(Long userId) {
        if (!UserCapability.isAllowed(requireUser(userId).getPermComment())) {
            throw new BizException(ResultCode.PERMISSION_DENIED_COMMENT);
        }
    }

    public void requireCanOrder(Long userId) {
        if (!UserCapability.isAllowed(requireUser(userId).getPermOrder())) {
            throw new BizException(ResultCode.PERMISSION_DENIED_ORDER);
        }
    }

    public void requireCanBroadcast(Long userId) {
        if (!UserCapability.isAllowed(requireUser(userId).getPermBroadcast())) {
            throw new BizException(ResultCode.PERMISSION_DENIED_BROADCAST);
        }
    }

    private User requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        return user;
    }
}
