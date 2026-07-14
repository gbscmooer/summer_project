package com.campus.user.service;

import com.campus.common.dto.UserPermissionsVO;
import com.campus.common.result.PageResult;
import com.campus.user.dto.AdminUserVO;
import com.campus.user.dto.BanUserRequest;
import com.campus.user.dto.UpdateUserPermissionsRequest;

public interface AdminUserService {

    PageResult<AdminUserVO> listUsers(Integer pageNum, Integer pageSize, String keyword);

    void banUser(Long adminId, Long targetUserId, BanUserRequest request);

    void unbanUser(Long adminId, Long targetUserId);

    UserPermissionsVO updatePermissions(Long adminId, Long targetUserId, UpdateUserPermissionsRequest request);
}
