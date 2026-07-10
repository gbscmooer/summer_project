package com.campus.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageParamUtilTest {

    @Test
    void normalizeInvalidPageNumToDefault() {
        assertEquals(1, PageParamUtil.normalizePageNum(null));
        assertEquals(1, PageParamUtil.normalizePageNum(0));
        assertEquals(1, PageParamUtil.normalizePageNum(-3));
    }

    @Test
    void normalizeInvalidAndOversizedPageSize() {
        assertEquals(10, PageParamUtil.normalizePageSize(null));
        assertEquals(10, PageParamUtil.normalizePageSize(0));
        assertEquals(10, PageParamUtil.normalizePageSize(-1));
        assertEquals(100, PageParamUtil.normalizePageSize(1000));
        assertEquals(20, PageParamUtil.normalizePageSize(20));
    }
}
