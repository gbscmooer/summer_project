package com.campus.user.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.campus.common.constant.UserRole;
import com.campus.common.exception.BizException;
import com.campus.common.result.ResultCode;
import com.campus.user.dto.SpecialCertApplicationRequest;
import com.campus.user.dto.SpecialCertApplicationVO;
import com.campus.user.entity.SpecialCertApplication;
import com.campus.user.entity.User;
import com.campus.user.mapper.SpecialCertApplicationMapper;
import com.campus.user.mapper.UserMapper;
import com.campus.user.service.SpecialCertApplicationService;
import com.campus.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SpecialCertApplicationServiceImpl
        extends ServiceImpl<SpecialCertApplicationMapper, SpecialCertApplication>
        implements SpecialCertApplicationService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_APPROVED = 1;
    private static final int STATUS_REJECTED = 2;

    private final UserService userService;
    private final UserMapper userMapper;

    @Override
    public void apply(Long userId, SpecialCertApplicationRequest request) {
        int role = userService.getRole(userId);
        if (role == UserRole.OFFICIAL || role == UserRole.ADMIN) {
            throw new BizException(ResultCode.SPECIAL_CERT_ALREADY);
        }
        if (role != UserRole.USER) {
            throw new BizException(ResultCode.SPECIAL_CERT_NOT_ELIGIBLE);
        }
        boolean pending = lambdaQuery()
                .eq(SpecialCertApplication::getUserId, userId)
                .eq(SpecialCertApplication::getStatus, STATUS_PENDING)
                .exists();
        if (pending) {
            throw new BizException(ResultCode.SPECIAL_CERT_APPLICATION_PENDING);
        }

        SpecialCertApplication app = new SpecialCertApplication();
        app.setUserId(userId);
        app.setDisplayName(request.getDisplayName().trim());
        app.setReason(request.getReason().trim());
        app.setContactPhone(request.getContactPhone().trim());
        app.setStatus(STATUS_PENDING);
        save(app);
    }

    @Override
    public SpecialCertApplicationVO getMyApplication(Long userId) {
        SpecialCertApplication app = lambdaQuery()
                .eq(SpecialCertApplication::getUserId, userId)
                .orderByDesc(SpecialCertApplication::getId)
                .last("LIMIT 1")
                .one();
        if (app == null) {
            return null;
        }
        User user = userMapper.selectById(userId);
        String username = user != null ? user.getUsername() : null;
        String nickname = user != null ? user.getNickname() : null;
        return SpecialCertApplicationVO.from(app, username, nickname);
    }

    @Override
    public List<SpecialCertApplicationVO> listPending() {
        List<SpecialCertApplication> apps = lambdaQuery()
                .eq(SpecialCertApplication::getStatus, STATUS_PENDING)
                .orderByAsc(SpecialCertApplication::getCreateTime)
                .list();
        if (apps.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = apps.stream().map(SpecialCertApplication::getUserId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        return apps.stream()
                .map(app -> {
                    User user = userMap.get(app.getUserId());
                    return SpecialCertApplicationVO.from(
                            app,
                            user != null ? user.getUsername() : null,
                            user != null ? user.getNickname() : null);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void approve(Long adminId, Long applicationId, String adminNote) {
        SpecialCertApplication app = getAndCheckPending(applicationId);
        User user = userMapper.selectById(app.getUserId());
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (user.getRole() != null && user.getRole() == UserRole.OFFICIAL) {
            throw new BizException(ResultCode.SPECIAL_CERT_ALREADY);
        }

        User rolePatch = new User();
        rolePatch.setId(app.getUserId());
        rolePatch.setRole(UserRole.OFFICIAL);
        if (app.getDisplayName() != null && !app.getDisplayName().isBlank()) {
            rolePatch.setNickname(app.getDisplayName().trim());
        }
        userMapper.updateById(rolePatch);

        SpecialCertApplication patch = new SpecialCertApplication();
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
        SpecialCertApplication patch = new SpecialCertApplication();
        patch.setId(applicationId);
        patch.setStatus(STATUS_REJECTED);
        patch.setAdminId(adminId);
        patch.setAdminNote(adminNote);
        updateById(patch);
    }

    private SpecialCertApplication getAndCheckPending(Long applicationId) {
        SpecialCertApplication app = getById(applicationId);
        if (app == null) {
            throw new BizException(ResultCode.SPECIAL_CERT_APPLICATION_NOT_FOUND);
        }
        if (app.getStatus() == null || app.getStatus() != STATUS_PENDING) {
            throw new BizException(ResultCode.SPECIAL_CERT_APPLICATION_REVIEWED);
        }
        return app;
    }
}
