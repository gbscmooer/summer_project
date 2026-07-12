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
    /** 返回全部用户 ID，供管理员广播通知使用。 */
    List<Long> listAllUserIds();
    /** 按用户名解析用户 ID，忽略不存在的用户名。 */
    List<Long> resolveUserIdsByUsernames(List<String> usernames);
    OnboardingStatusResponse getOnboardingStatus(Long userId);
    void markOnboardingStep(Long userId, String step);
    void completeOnboarding(Long userId);

    /** 订单评价后增量更新卖家信誉：review_count+1 并重算 avg_rating。 */
    void applyRating(Long sellerId, int rating);
}
