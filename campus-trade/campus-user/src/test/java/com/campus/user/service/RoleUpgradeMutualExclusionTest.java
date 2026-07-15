package com.campus.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.campus.common.constant.UserRole;
import com.campus.common.exception.BizException;
import com.campus.common.result.ResultCode;
import com.campus.user.dto.MerchantApplicationRequest;
import com.campus.user.entity.MerchantApplication;
import com.campus.user.entity.SpecialCertApplication;
import com.campus.user.entity.User;
import com.campus.user.mapper.MerchantApplicationMapper;
import com.campus.user.mapper.SpecialCertApplicationMapper;
import com.campus.user.mapper.UserMapper;
import com.campus.user.service.impl.MerchantApplicationServiceImpl;
import com.campus.user.service.impl.SpecialCertApplicationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 角色互斥：商家与特殊认证审核不得互相覆盖 t_user.role。
 */
@ExtendWith(MockitoExtension.class)
class RoleUpgradeMutualExclusionTest {

    @Mock
    private UserService userService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private MerchantApplicationMapper merchantApplicationMapper;
    @Mock
    private SpecialCertApplicationMapper specialCertApplicationMapper;

    private SpecialCertApplicationServiceImpl specialCertService;
    private MerchantApplicationServiceImpl merchantService;

    @BeforeEach
    void setUp() {
        specialCertService = new SpecialCertApplicationServiceImpl(
                userService, userMapper, merchantApplicationMapper);
        ReflectionTestUtils.setField(specialCertService, "baseMapper", specialCertApplicationMapper);

        merchantService = new MerchantApplicationServiceImpl(
                userService, userMapper, specialCertApplicationMapper);
        ReflectionTestUtils.setField(merchantService, "baseMapper", merchantApplicationMapper);
    }

    @Test
    void specialCertApprove_rejectsWhenUserAlreadyMerchant() {
        SpecialCertApplication app = pendingSpecialCert(11L, 100L);
        when(specialCertApplicationMapper.selectById(11L)).thenReturn(app);
        when(userMapper.selectById(100L)).thenReturn(user(100L, UserRole.MERCHANT));

        BizException ex = assertThrows(BizException.class,
                () -> specialCertService.approve(1L, 11L, null));
        assertEquals(ResultCode.SPECIAL_CERT_NOT_ELIGIBLE.getCode(), ex.getCode());
        verify(userMapper, never()).upgradeRoleIfPersonal(anyLong(), anyInt(), nullable(String.class));
    }

    @Test
    void merchantApprove_rejectsWhenUserAlreadyOfficial() {
        MerchantApplication app = pendingMerchant(22L, 200L);
        when(merchantApplicationMapper.selectById(22L)).thenReturn(app);
        when(userMapper.selectById(200L)).thenReturn(user(200L, UserRole.OFFICIAL));

        BizException ex = assertThrows(BizException.class,
                () -> merchantService.approve(1L, 22L, null));
        assertEquals(ResultCode.MERCHANT_NOT_ELIGIBLE.getCode(), ex.getCode());
        verify(userMapper, never()).upgradeRoleIfPersonal(anyLong(), anyInt(), nullable(String.class));
    }

    @Test
    void merchantApply_rejectsOfficialRole() {
        when(userService.getRole(300L)).thenReturn(UserRole.OFFICIAL);

        BizException ex = assertThrows(BizException.class,
                () -> merchantService.apply(300L, request("店", "理由", "13800000000")));
        assertEquals(ResultCode.MERCHANT_NOT_ELIGIBLE.getCode(), ex.getCode());
        verify(merchantApplicationMapper, never()).insert(any(MerchantApplication.class));
    }

    @Test
    void specialCertApprove_rejectsWhenConcurrentRoleCasLoses() {
        SpecialCertApplication app = pendingSpecialCert(11L, 100L);
        when(specialCertApplicationMapper.selectById(11L)).thenReturn(app);
        when(userMapper.selectById(100L)).thenReturn(user(100L, UserRole.USER));
        // 并发商家审核已抢先把 role 改成 MERCHANT，CAS 更新 0 行
        when(userMapper.upgradeRoleIfPersonal(eq(100L), eq(UserRole.OFFICIAL), anyString())).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> specialCertService.approve(1L, 11L, null));
        assertEquals(ResultCode.SPECIAL_CERT_NOT_ELIGIBLE.getCode(), ex.getCode());
        verify(specialCertApplicationMapper, never()).update(isNull(), ArgumentMatchers.<Wrapper<SpecialCertApplication>>any());
    }

    @Test
    void merchantApprove_rejectsWhenConcurrentRoleCasLoses() {
        MerchantApplication app = pendingMerchant(22L, 200L);
        when(merchantApplicationMapper.selectById(22L)).thenReturn(app);
        when(userMapper.selectById(200L)).thenReturn(user(200L, UserRole.USER));
        when(userMapper.upgradeRoleIfPersonal(eq(200L), eq(UserRole.MERCHANT), anyString())).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> merchantService.approve(1L, 22L, null));
        assertEquals(ResultCode.MERCHANT_NOT_ELIGIBLE.getCode(), ex.getCode());
        verify(merchantApplicationMapper, never()).update(isNull(), ArgumentMatchers.<Wrapper<MerchantApplication>>any());
    }

    @Test
    void specialCertApprove_usesCasThenCancelsPendingMerchant() {
        SpecialCertApplication app = pendingSpecialCert(11L, 100L);
        when(specialCertApplicationMapper.selectById(11L)).thenReturn(app);
        when(userMapper.selectById(100L)).thenReturn(user(100L, UserRole.USER));
        when(userMapper.upgradeRoleIfPersonal(eq(100L), eq(UserRole.OFFICIAL), eq("校园集市官方"))).thenReturn(1);
        when(specialCertApplicationMapper.update(isNull(), ArgumentMatchers.<Wrapper<SpecialCertApplication>>any()))
                .thenReturn(1);
        when(merchantApplicationMapper.update(isNull(), ArgumentMatchers.<Wrapper<MerchantApplication>>any()))
                .thenReturn(1);

        specialCertService.approve(1L, 11L, "ok");

        verify(userMapper).upgradeRoleIfPersonal(100L, UserRole.OFFICIAL, "校园集市官方");
        verify(specialCertApplicationMapper).update(isNull(), ArgumentMatchers.<Wrapper<SpecialCertApplication>>any());
        verify(merchantApplicationMapper).update(isNull(), ArgumentMatchers.<Wrapper<MerchantApplication>>any());
    }

    @Test
    void merchantReject_failsWhenConcurrentApproveAlreadyClaimed() {
        MerchantApplication app = pendingMerchant(22L, 200L);
        when(merchantApplicationMapper.selectById(22L)).thenReturn(app);
        // 并发 approve 已把 status 改为 APPROVED，拒绝 CAS 更新 0 行
        when(merchantApplicationMapper.update(isNull(), ArgumentMatchers.<Wrapper<MerchantApplication>>any()))
                .thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> merchantService.reject(1L, 22L, "不通过"));
        assertEquals(ResultCode.MERCHANT_APPLICATION_REVIEWED.getCode(), ex.getCode());
    }

    @Test
    void specialCertReject_failsWhenConcurrentApproveAlreadyClaimed() {
        SpecialCertApplication app = pendingSpecialCert(11L, 100L);
        when(specialCertApplicationMapper.selectById(11L)).thenReturn(app);
        when(specialCertApplicationMapper.update(isNull(), ArgumentMatchers.<Wrapper<SpecialCertApplication>>any()))
                .thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> specialCertService.reject(1L, 11L, "不通过"));
        assertEquals(ResultCode.SPECIAL_CERT_APPLICATION_REVIEWED.getCode(), ex.getCode());
    }

    @Test
    void merchantReject_usesCasOnPending() {
        MerchantApplication app = pendingMerchant(22L, 200L);
        when(merchantApplicationMapper.selectById(22L)).thenReturn(app);
        when(merchantApplicationMapper.update(isNull(), ArgumentMatchers.<Wrapper<MerchantApplication>>any()))
                .thenReturn(1);

        merchantService.reject(1L, 22L, "材料不全");

        verify(merchantApplicationMapper).update(isNull(), ArgumentMatchers.<Wrapper<MerchantApplication>>any());
        verify(merchantApplicationMapper, never()).updateById(any(MerchantApplication.class));
    }

    private static User user(Long id, int role) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        return u;
    }

    private static SpecialCertApplication pendingSpecialCert(Long id, Long userId) {
        SpecialCertApplication app = new SpecialCertApplication();
        app.setId(id);
        app.setUserId(userId);
        app.setDisplayName("校园集市官方");
        app.setStatus(0);
        return app;
    }

    private static MerchantApplication pendingMerchant(Long id, Long userId) {
        MerchantApplication app = new MerchantApplication();
        app.setId(id);
        app.setUserId(userId);
        app.setShopName("小店");
        app.setStatus(0);
        return app;
    }

    private static MerchantApplicationRequest request(String shop, String reason, String phone) {
        MerchantApplicationRequest req = new MerchantApplicationRequest();
        req.setShopName(shop);
        req.setReason(reason);
        req.setContactPhone(phone);
        return req;
    }
}
