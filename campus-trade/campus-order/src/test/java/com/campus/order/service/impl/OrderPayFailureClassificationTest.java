package com.campus.order.service.impl;

import com.campus.common.exception.BizException;
import com.campus.common.result.ResultCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 支付失败分类：积分侧已确认未划转的业务码必须回滚订单，避免「PAID 未扣款」后被确认收货。
 */
class OrderPayFailureClassificationTest {

    @Test
    void userBannedIsDefinitivePayFailure() {
        assertTrue(OrderServiceImpl.isDefinitivePayFailure(
                new BizException(ResultCode.USER_BANNED)));
    }

    @Test
    void knownDefinitiveFailuresIncludeInsufficientAndMissingUser() {
        assertTrue(OrderServiceImpl.isDefinitivePayFailure(
                new BizException(ResultCode.POINTS_INSUFFICIENT)));
        assertTrue(OrderServiceImpl.isDefinitivePayFailure(
                new BizException(ResultCode.BAD_REQUEST)));
        assertTrue(OrderServiceImpl.isDefinitivePayFailure(
                new BizException(ResultCode.USER_NOT_FOUND)));
        assertTrue(OrderServiceImpl.isDefinitivePayFailure(
                new BizException(ResultCode.NOT_FOUND)));
    }

    @Test
    void unknownBusinessCodesAreNotDefinitive() {
        // 未知业务码保持 PAID，依赖幂等重试；不得误回滚已可能成功的划转
        assertFalse(OrderServiceImpl.isDefinitivePayFailure(
                new BizException(ResultCode.INTERNAL_ERROR)));
        assertFalse(OrderServiceImpl.isDefinitivePayFailure(
                new BizException(9999, "unknown downstream")));
    }
}
