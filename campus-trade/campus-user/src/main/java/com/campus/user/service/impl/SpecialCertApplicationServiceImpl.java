package com.campus.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.campus.common.constant.UserRole;
import com.campus.common.exception.BizException;
import com.campus.common.result.ResultCode;
import com.campus.user.dto.SpecialCertApplicationRequest;
import com.campus.user.dto.SpecialCertApplicationVO;
import com.campus.user.entity.MerchantApplication;
import com.campus.user.entity.SpecialCertApplication;
import com.campus.user.entity.User;
import com.campus.user.mapper.MerchantApplicationMapper;
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
    private final MerchantApplicationMapper merchantApplicationMapper;

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
        int currentRole = user.getRole() == null ? UserRole.USER : user.getRole();
        // 商家/管理员等非个人账户不得被覆盖为特殊认证，否则会丢掉原角色权限
        if (currentRole == UserRole.OFFICIAL) {
            throw new BizException(ResultCode.SPECIAL_CERT_ALREADY);
        }
        if (!UserRole.canUpgradeRole(currentRole)) {
            throw new BizException(ResultCode.SPECIAL_CERT_NOT_ELIGIBLE);
        }

        // CAS：仅当仍为个人账户时升级，防止与商家审核并发时互相覆盖 role
        LambdaUpdateWrapper<User> roleUpdate = new LambdaUpdateWrapper<User>()
                .eq(User::getId, app.getUserId())
                .and(w -> w.eq(User::getRole, UserRole.USER).or().isNull(User::getRole))
                .set(User::getRole, UserRole.OFFICIAL);
        if (app.getDisplayName() != null && !app.getDisplayName().isBlank()) {
            roleUpdate.set(User::getNickname, app.getDisplayName().trim());
        }
        if (userMapper.update(null, roleUpdate) == 0) {
            throw new BizException(ResultCode.SPECIAL_CERT_NOT_ELIGIBLE);
        }

        // CAS：认领待审申请，避免与并发拒绝/另一路审核重复处理
        boolean claimed = update(new LambdaUpdateWrapper<SpecialCertApplication>()
                .eq(SpecialCertApplication::getId, applicationId)
                .eq(SpecialCertApplication::getStatus, STATUS_PENDING)
                .set(SpecialCertApplication::getStatus, STATUS_APPROVED)
                .set(SpecialCertApplication::getAdminId, adminId)
                .set(SpecialCertApplication::getAdminNote, adminNote));
        if (!claimed) {
            throw new BizException(ResultCode.SPECIAL_CERT_APPLICATION_REVIEWED);
        }

        // 角色互斥：关闭仍待审的商家申请，避免后续误审覆盖 OFFICIAL
        cancelPendingMerchantApplications(app.getUserId(), adminId);
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

    private void cancelPendingMerchantApplications(Long userId, Long adminId) {
        merchantApplicationMapper.update(
                null,
                new LambdaUpdateWrapper<MerchantApplication>()
                        .eq(MerchantApplication::getUserId, userId)
                        .eq(MerchantApplication::getStatus, STATUS_PENDING)
                        .set(MerchantApplication::getStatus, STATUS_REJECTED)
                        .set(MerchantApplication::getAdminId, adminId)
                        .set(MerchantApplication::getAdminNote, "已通过特殊认证，自动关闭互斥的商家申请"));
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
