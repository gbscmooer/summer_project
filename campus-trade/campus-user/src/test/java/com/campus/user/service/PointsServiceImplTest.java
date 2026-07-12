package com.campus.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.campus.common.exception.BizException;
import com.campus.common.result.ResultCode;
import com.campus.user.dto.PointsTransferRequest;
import com.campus.user.entity.PointLedger;
import com.campus.user.entity.User;
import com.campus.user.mapper.DailyCheckinMapper;
import com.campus.user.mapper.DailyLikeQuestItemMapper;
import com.campus.user.mapper.DailyLikeQuestMapper;
import com.campus.user.mapper.PointLedgerMapper;
import com.campus.user.mapper.UserMapper;
import com.campus.user.service.impl.PointsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointsServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private PointLedgerMapper pointLedgerMapper;
    @Mock
    private DailyCheckinMapper dailyCheckinMapper;
    @Mock
    private DailyLikeQuestMapper dailyLikeQuestMapper;
    @Mock
    private DailyLikeQuestItemMapper dailyLikeQuestItemMapper;

    private PointsServiceImpl pointsService;

    @BeforeEach
    void setUp() {
        pointsService = new PointsServiceImpl(
                userMapper,
                pointLedgerMapper,
                dailyCheckinMapper,
                dailyLikeQuestMapper,
                dailyLikeQuestItemMapper);
    }

    @Test
    void transferForOrder_deductsBuyerAndCreditsSellerWithSplitReasons() {
        User buyer = user(1L, 100);
        User seller = user(2L, 50);
        stubLockPair(buyer, seller);
        when(pointLedgerMapper.selectOne(any())).thenReturn(null);
        when(userMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        when(userMapper.selectById(1L)).thenReturn(user(1L, 70));
        when(userMapper.selectById(2L)).thenReturn(user(2L, 80));
        when(pointLedgerMapper.insert(any(PointLedger.class))).thenReturn(1);

        PointsTransferRequest req = transfer(1L, 2L, 30, "ORDER_PAY", "ORDER", "99");
        pointsService.transferForOrder(req);

        ArgumentCaptor<PointLedger> captor = ArgumentCaptor.forClass(PointLedger.class);
        verify(pointLedgerMapper, times(2)).insert(captor.capture());
        PointLedger debit = captor.getAllValues().stream()
                .filter(l -> PointsConstants.REASON_ORDER_PAY_DEBIT.equals(l.getReason()))
                .findFirst().orElseThrow();
        PointLedger credit = captor.getAllValues().stream()
                .filter(l -> PointsConstants.REASON_ORDER_PAY_CREDIT.equals(l.getReason()))
                .findFirst().orElseThrow();
        assertEquals(-30, debit.getDelta());
        assertEquals(70, debit.getBalanceAfter());
        assertEquals("ORDER", debit.getRefType());
        assertEquals("99", debit.getRefId());
        assertEquals(30, credit.getDelta());
        assertEquals(80, credit.getBalanceAfter());
    }

    @Test
    void transferForOrder_isIdempotentWhenDebitLedgerExists() {
        User buyer = user(1L, 100);
        User seller = user(2L, 50);
        stubLockPair(buyer, seller);
        PointLedger existing = new PointLedger();
        existing.setReason(PointsConstants.REASON_ORDER_PAY_DEBIT);
        when(pointLedgerMapper.selectOne(any())).thenReturn(existing);

        pointsService.transferForOrder(transfer(1L, 2L, 30, "ORDER_PAY", "ORDER", "99"));

        verify(userMapper, never()).update(any(), any(Wrapper.class));
        verify(pointLedgerMapper, never()).insert(any(PointLedger.class));
    }

    @Test
    void transferForOrder_throwsWhenInsufficientPoints() {
        User buyer = user(1L, 10);
        User seller = user(2L, 50);
        stubLockPair(buyer, seller);
        when(pointLedgerMapper.selectOne(any())).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> pointsService.transferForOrder(transfer(1L, 2L, 30, "ORDER_PAY", "ORDER", "1")));
        assertEquals(ResultCode.POINTS_INSUFFICIENT.getCode(), ex.getCode());
    }

    @Test
    void transferTip_returnsFalseOnDuplicateLedgerInsert() {
        User from = user(3L, 100);
        User to = user(4L, 20);
        stubLockPair(from, to);
        when(pointLedgerMapper.selectOne(any())).thenReturn(null);
        when(userMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        when(userMapper.selectById(3L)).thenReturn(user(3L, 90));
        when(pointLedgerMapper.insert(any(PointLedger.class)))
                .thenThrow(new DuplicateKeyException("uk_user_ref_reason"));

        boolean applied = pointsService.transferTip(
                transfer(3L, 4L, 10, null, "TOPIC_TIP", "post-1"));
        assertFalse(applied);
        verify(pointLedgerMapper, atLeast(1)).insert(any(PointLedger.class));
    }

    @Test
    void transferTip_successWritesDebitAndCredit() {
        User from = user(5L, 100);
        User to = user(6L, 0);
        stubLockPair(from, to);
        when(pointLedgerMapper.selectOne(any())).thenReturn(null);
        when(userMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        when(userMapper.selectById(5L)).thenReturn(user(5L, 95));
        when(userMapper.selectById(6L)).thenReturn(user(6L, 5));
        when(pointLedgerMapper.insert(any(PointLedger.class))).thenReturn(1);

        assertTrue(pointsService.transferTip(
                transfer(5L, 6L, 5, null, PointsConstants.REF_TOPIC_TIP, "42")));

        ArgumentCaptor<PointLedger> captor = ArgumentCaptor.forClass(PointLedger.class);
        verify(pointLedgerMapper, times(2)).insert(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .anyMatch(l -> PointsConstants.REASON_TOPIC_TIP_DEBIT.equals(l.getReason())
                        && l.getDelta() == -5));
        assertTrue(captor.getAllValues().stream()
                .anyMatch(l -> PointsConstants.REASON_TOPIC_TIP_CREDIT.equals(l.getReason())
                        && l.getDelta() == 5));
    }

    @SuppressWarnings("unchecked")
    private void stubLockPair(User a, User b) {
        Long firstId = a.getId() <= b.getId() ? a.getId() : b.getId();
        Long secondId = a.getId() <= b.getId() ? b.getId() : a.getId();
        User first = a.getId().equals(firstId) ? a : b;
        User second = b.getId().equals(secondId) ? b : a;
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(first, second);
    }

    private static User user(Long id, int points) {
        User u = new User();
        u.setId(id);
        u.setPoints(points);
        u.setStatus(0);
        return u;
    }

    private static PointsTransferRequest transfer(
            Long from, Long to, int amount, String reason, String refType, String refId) {
        PointsTransferRequest req = new PointsTransferRequest();
        req.setFromUserId(from);
        req.setToUserId(to);
        req.setAmount(amount);
        req.setReason(reason);
        req.setRefType(refType);
        req.setRefId(refId);
        return req;
    }
}
