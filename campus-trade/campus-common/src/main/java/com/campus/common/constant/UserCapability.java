package com.campus.common.constant;

/**
 * 用户细粒度能力开关，对应 t_user.perm_*（1-允许 0-禁止；null 视为允许以兼容旧数据）。
 */
public final class UserCapability {

    private UserCapability() {
    }

    /** 是否允许：null 或非 0 视为允许。 */
    public static boolean isAllowed(Integer flag) {
        return flag == null || flag != 0;
    }

    public static int toFlag(boolean allowed) {
        return allowed ? 1 : 0;
    }
}
