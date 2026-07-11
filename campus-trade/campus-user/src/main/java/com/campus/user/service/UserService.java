package com.campus.user.service;

import com.campus.user.dto.*;

import java.util.List;

public interface UserService {
    Long register(RegisterRequest request);
    LoginResponse login(LoginRequest request);
    UserInfoResponse getUserInfo(Long userId);
    void updateUserInfo(Long userId, UpdateUserRequest request);
    List<UserBriefVO> batchGetUsers(List<Long> ids);
    /** 返回角色：0-普通用户 1-管理员 */
    int getRole(Long userId);
}
