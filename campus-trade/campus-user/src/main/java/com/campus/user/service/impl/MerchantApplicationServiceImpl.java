package com.campus.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.campus.common.constant.UserRole;
import com.campus.common.exception.BizException;
import com.campus.common.result.ResultCode;
import com.campus.user.dto.MerchantApplicationRequest;
import com.campus.user.dto.MerchantApplicationVO;
import com.campus.user.entity.MerchantApplication;
import com.campus.user.entity.SpecialCertApplication;
import com.campus.user.entity.User;
import com.campus.user.mapper.MerchantApplicationMapper;
import com.campus.user.mapper.SpecialCertApplicationMapper;
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
    private final SpecialCertApplicationMapper specialCertApplicationMapper;

    @Override
    public void apply(Long userId, MerchantApplicationRequest request) {
        int role = userService.getRole(userId);
        if (role == UserRole.MERCHANT) {
            throw new BizException(ResultCode.MERCHANT_ALREADY);
        }
        // 管理员、特殊认证等与商家互斥，不可再申请商家（避免审核通过后覆盖 OFFICIAL 权限）
        if (!UserRole.canUpgradeRole(role)) {
            throw new BizException(ResultCode.MERCHANT_NOT_ELIGIBLE);
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
        int currentRole = user.getRole() == null ? UserRole.USER : user.getRole();
        // 特殊认证/管理员等非个人账户不得被覆盖为商家，否则会丢掉通知发送等权限
        if (currentRole == UserRole.MERCHANT) {
            throw new BizException(ResultCode.MERCHANT_ALREADY);
        }
        if (!UserRole.canUpgradeRole(currentRole)) {
            throw new BizException(ResultCode.MERCHANT_NOT_ELIGIBLE);
        }

        // CAS：仅当仍为个人账户时升级，防止与特殊认证审核并发时互相覆盖 role
        LambdaUpdateWrapper<User> roleUpdate = new LambdaUpdateWrapper<User>()
                .eq(User::getId, app.getUserId())
                .and(w -> w.eq(User::getRole, UserRole.USER).or().isNull(User::getRole))
                .set(User::getRole, UserRole.MERCHANT);
        if (app.getShopName() != null && !app.getShopName().isBlank()) {
            roleUpdate.set(User::getNickname, app.getShopName().trim());
        }
        if (userMapper.update(null, roleUpdate) == 0) {
            throw new BizException(ResultCode.MERCHANT_NOT_ELIGIBLE);
        }

        // CAS：认领待审申请，避免与并发拒绝/另一路审核重复处理
        boolean claimed = update(new LambdaUpdateWrapper<MerchantApplication>()
                .eq(MerchantApplication::getId, applicationId)
                .eq(MerchantApplication::getStatus, STATUS_PENDING)
                .set(MerchantApplication::getStatus, STATUS_APPROVED)
                .set(MerchantApplication::getAdminId, adminId)
                .set(MerchantApplication::getAdminNote, adminNote));
        if (!claimed) {
            throw new BizException(ResultCode.MERCHANT_APPLICATION_REVIEWED);
        }

        // 角色互斥：关闭仍待审的特殊认证申请，避免后续误审覆盖 MERCHANT
        cancelPendingSpecialCertApplications(app.getUserId(), adminId);
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

    private void cancelPendingSpecialCertApplications(Long userId, Long adminId) {
        specialCertApplicationMapper.update(
                null,
                new LambdaUpdateWrapper<SpecialCertApplication>()
                        .eq(SpecialCertApplication::getUserId, userId)
                        .eq(SpecialCertApplication::getStatus, STATUS_PENDING)
                        .set(SpecialCertApplication::getStatus, STATUS_REJECTED)
                        .set(SpecialCertApplication::getAdminId, adminId)
                        .set(SpecialCertApplication::getAdminNote, "已通过商家认证，自动关闭互斥的特殊认证申请"));
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
