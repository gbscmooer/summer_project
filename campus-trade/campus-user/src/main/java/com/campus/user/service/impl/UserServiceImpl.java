package com.campus.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.campus.common.constant.UserStatus;
import com.campus.common.exception.BizException;
import com.campus.common.result.ResultCode;
import com.campus.common.util.JwtUtil;
import com.campus.user.dto.*;
import com.campus.user.entity.User;
import com.campus.user.mapper.UserMapper;
import com.campus.user.service.OnboardingFlagCodec;
import com.campus.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public Long register(RegisterRequest request) {
        boolean exists = lambdaQuery()
                .eq(User::getUsername, request.getUsername())
                .exists();
        if (exists) {
            throw new BizException(ResultCode.USERNAME_EXISTS);
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        user.setPhone(request.getPhone());
        user.setRole(0);
        user.setStatus(UserStatus.ACTIVE);
        user.setOnboardingCompleted(0);
        user.setOnboardingFlags("{}");

        try {
            save(user);
        } catch (DataIntegrityViolationException e) {
            throw new BizException(ResultCode.USERNAME_EXISTS);
        }
        return user.getId();
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = lambdaQuery()
                .eq(User::getUsername, request.getUsername())
                .one();
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }
        if (UserStatus.isEffectivelyBanned(user.getStatus(), user.getBanUntil())) {
            String message = ResultCode.USER_BANNED.getMessage();
            if (StringUtils.hasText(user.getBanReason())) {
                message = message + "（原因：" + user.getBanReason().trim() + "）";
            }
            if (user.getBanUntil() != null) {
                message = message + "，将于 " + user.getBanUntil() + " 解封";
            }
            throw new BizException(ResultCode.USER_BANNED.getCode(), message);
        }
        if (UserStatus.isBanned(user.getStatus())) {
            clearExpiredBan(user.getId());
        }
        if (user.getId() == null) {
            throw new BizException(ResultCode.INTERNAL_ERROR);
        }
        String token = JwtUtil.generateToken(user.getId());
        int role = user.getRole() == null ? 0 : user.getRole();
        int onboardingCompleted = user.getOnboardingCompleted() == null ? 0 : user.getOnboardingCompleted();
        return new LoginResponse(token, user.getId(), user.getNickname(), user.getAvatar(), role, onboardingCompleted);
    }

    @Override
    public UserInfoResponse getUserInfo(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return UserInfoResponse.from(user);
    }

    @Override
    public void updateUserInfo(Long userId, UpdateUserRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        boolean hasUpdates = false;
        User user = new User();
        user.setId(userId);
        if (StringUtils.hasText(request.getNickname())) {
            user.setNickname(request.getNickname());
            hasUpdates = true;
        }
        if (StringUtils.hasText(request.getAvatar())) {
            user.setAvatar(request.getAvatar());
            hasUpdates = true;
        }
        if (StringUtils.hasText(request.getPhone())) {
            user.setPhone(request.getPhone());
            hasUpdates = true;
        }
        if (hasUpdates) {
            updateById(user);
        }
    }

    @Override
    public List<UserBriefVO> batchGetUsers(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return listByIds(ids).stream()
                .map(u -> new UserBriefVO(u.getId(), u.getNickname()))
                .collect(Collectors.toList());
    }

    @Override
    public int getRole(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return user.getRole() == null ? 0 : user.getRole();
    }

    @Override
    public List<Long> listAllUserIds() {
        return lambdaQuery()
                .select(User::getId)
                .list()
                .stream()
                .map(User::getId)
                .filter(id -> id != null)
                .collect(Collectors.toList());
    }

    @Override
    public List<Long> resolveUserIdsByUsernames(List<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return List.of();
        }
        List<String> normalized = usernames.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        if (normalized.isEmpty()) {
            return List.of();
        }
        return lambdaQuery()
                .in(User::getUsername, normalized)
                .list()
                .stream()
                .map(User::getId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public OnboardingStatusResponse getOnboardingStatus(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        int completed = user.getOnboardingCompleted() == null ? 0 : user.getOnboardingCompleted();
        OnboardingStatusResponse response = new OnboardingStatusResponse();
        response.setCompleted(completed == 1);
        response.setVisible(completed != 1);
        response.setFlags(OnboardingFlagCodec.decode(user.getOnboardingFlags()));
        return response;
    }

    @Override
    public void markOnboardingStep(Long userId, String step) {
        if (!OnboardingFlagCodec.isAllowedStep(step)) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        User user = getById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (user.getOnboardingCompleted() != null && user.getOnboardingCompleted() == 1) {
            return;
        }
        Map<String, Boolean> flags = OnboardingFlagCodec.decode(user.getOnboardingFlags());
        flags.put(step, true);
        User patch = new User();
        patch.setId(userId);
        patch.setOnboardingFlags(OnboardingFlagCodec.encode(flags));
        updateById(patch);
    }

    @Override
    public void completeOnboarding(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (user.getOnboardingCompleted() != null && user.getOnboardingCompleted() == 1) {
            return;
        }
        User patch = new User();
        patch.setId(userId);
        patch.setOnboardingCompleted(1);
        updateById(patch);
    }

    private void clearExpiredBan(Long userId) {
        lambdaUpdate()
                .eq(User::getId, userId)
                .set(User::getStatus, UserStatus.ACTIVE)
                .set(User::getBanReason, null)
                .set(User::getBanUntil, null)
                .set(User::getBannedBy, null)
                .set(User::getBannedAt, null)
                .update();
    }
}
