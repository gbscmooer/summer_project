package com.campus.user.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.campus.common.constant.UserRole;
import com.campus.common.exception.BizException;
import com.campus.common.result.ResultCode;
import com.campus.user.dto.MerchantApplicationRequest;
import com.campus.user.dto.MerchantApplicationVO;
import com.campus.user.entity.MerchantApplication;
import com.campus.user.entity.User;
import com.campus.user.mapper.MerchantApplicationMapper;
import com.campus.user.mapper.UserMapper;
import com.campus.user.service.MerchantApplicationService;
import com.campus.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MerchantApplicationServiceImpl
        extends ServiceImpl<MerchantApplicationMapper, MerchantApplication>
        implements MerchantApplicationService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_APPROVED = 1;
    private static final int STATUS_REJECTED = 2;

    private final UserService userService;
    private final UserMapper userMapper;

    @Override
    public void apply(Long userId, MerchantApplicationRequest request) {
        int role = userService.getRole(userId);
        if (role == UserRole.MERCHANT) {
            throw new BizException(ResultCode.MERCHANT_ALREADY);
        }
        if (role == UserRole.ADMIN) {
            throw new BizException(ResultCode.MERCHANT_ALREADY);
        }
        boolean pending = lambdaQuery()
                .eq(MerchantApplication::getUserId, userId)
                .eq(MerchantApplication::getStatus, STATUS_PENDING)
                .exists();
        if (pending) {
            throw new BizException(ResultCode.MERCHANT_APPLICATION_PENDING);
        }

        MerchantApplication app = new MerchantApplication();
        app.setUserId(userId);
        app.setShopName(request.getShopName().trim());
        app.setReason(request.getReason().trim());
        app.setContactPhone(request.getContactPhone().trim());
        app.setStatus(STATUS_PENDING);
        save(app);
    }

    @Override
    public MerchantApplicationVO getMyApplication(Long userId) {
        MerchantApplication app = lambdaQuery()
                .eq(MerchantApplication::getUserId, userId)
                .orderByDesc(MerchantApplication::getId)
                .last("LIMIT 1")
                .one();
        if (app == null) {
            return null;
        }
        User user = userMapper.selectById(userId);
        String username = user != null ? user.getUsername() : null;
        String nickname = user != null ? user.getNickname() : null;
        return MerchantApplicationVO.from(app, username, nickname);
    }

    @Override
    public List<MerchantApplicationVO> listPending() {
        List<MerchantApplication> apps = lambdaQuery()
                .eq(MerchantApplication::getStatus, STATUS_PENDING)
                .orderByAsc(MerchantApplication::getCreateTime)
                .list();
        if (apps.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = apps.stream().map(MerchantApplication::getUserId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        return apps.stream()
                .map(app -> {
                    User user = userMap.get(app.getUserId());
                    return MerchantApplicationVO.from(
                            app,
                            user != null ? user.getUsername() : null,
                            user != null ? user.getNickname() : null);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void approve(Long adminId, Long applicationId, String adminNote) {
        MerchantApplication app = getAndCheckPending(applicationId);
        User user = userMapper.selectById(app.getUserId());
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (user.getRole() != null && user.getRole() == UserRole.MERCHANT) {
            throw new BizException(ResultCode.MERCHANT_ALREADY);
        }

        User rolePatch = new User();
        rolePatch.setId(app.getUserId());
        rolePatch.setRole(UserRole.MERCHANT);
        if (app.getShopName() != null && !app.getShopName().isBlank()) {
            rolePatch.setNickname(app.getShopName().trim());
        }
        userMapper.updateById(rolePatch);

        MerchantApplication patch = new MerchantApplication();
        patch.setId(applicationId);
        patch.setStatus(STATUS_APPROVED);
        patch.setAdminId(adminId);
        patch.setAdminNote(adminNote);
        updateById(patch);
    }

    @Override
    @Transactional
    public void reject(Long adminId, Long applicationId, String adminNote) {
        getAndCheckPending(applicationId);
        MerchantApplication patch = new MerchantApplication();
        patch.setId(applicationId);
        patch.setStatus(STATUS_REJECTED);
        patch.setAdminId(adminId);
        patch.setAdminNote(adminNote);
        updateById(patch);
    }

    private MerchantApplication getAndCheckPending(Long applicationId) {
        MerchantApplication app = getById(applicationId);
        if (app == null) {
            throw new BizException(ResultCode.MERCHANT_APPLICATION_NOT_FOUND);
        }
        if (app.getStatus() == null || app.getStatus() != STATUS_PENDING) {
            throw new BizException(ResultCode.MERCHANT_APPLICATION_REVIEWED);
        }
        return app;
    }
}
