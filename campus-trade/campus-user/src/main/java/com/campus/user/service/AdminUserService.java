package com.campus.user.service;

import com.campus.common.result.PageResult;
import com.campus.user.dto.AdminUserVO;
import com.campus.user.dto.BanUserRequest;

public interface AdminUserService {

    PageResult<AdminUserVO> listUsers(Integer pageNum, Integer pageSize, String keyword);

    void banUser(Long adminId, Long targetUserId, BanUserRequest request);

    void unbanUser(Long adminId, Long targetUserId);
}
