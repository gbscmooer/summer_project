package com.campus.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BizException;
import com.campus.common.result.ResultCode;
import com.campus.common.util.JwtUtil;
import com.campus.user.dto.*;
import com.campus.user.entity.User;
import com.campus.user.mapper.UserMapper;
import com.campus.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
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
        if (user.getId() == null) {
            throw new BizException(ResultCode.INTERNAL_ERROR);
        }
        String token = JwtUtil.generateToken(user.getId());
        return new LoginResponse(token, user.getId(), user.getNickname(), user.getAvatar());
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
                .map(u -> new UserBriefVO(u.getId(), u.getNickname(), u.getPhone()))
                .collect(Collectors.toList());
    }
}
