package com.campus.common.util;

/**
 * 分页参数兜底工具。
 *
 * <p>Controller 已提供默认值，但外部仍可能传入 0、负数或过大的 pageSize。
 * 在 Service 层统一归一化，避免分页插件生成异常或过大的查询。
 */
public final class PageParamUtil {

    public static final int DEFAULT_PAGE_NUM = 1;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;

    private PageParamUtil() {
    }

    public static int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
    }

    public static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
