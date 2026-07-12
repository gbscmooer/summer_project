package com.campus.common.constant;

import java.time.LocalDateTime;

/**
 * 用户账号状态，对应 t_user.status。
 */
public final class UserStatus {

    /** 正常 */
    public static final int ACTIVE = 0;
    /** 已封禁 */
    public static final int BANNED = 1;

    private UserStatus() {
    }

    public static boolean isBanned(Integer status) {
        return status != null && status == BANNED;
    }

    /** 封禁未过期（永久封禁或未到 banUntil）。 */
    public static boolean isEffectivelyBanned(Integer status, LocalDateTime banUntil) {
        if (!isBanned(status)) {
            return false;
        }
        if (banUntil == null) {
            return true;
        }
        return LocalDateTime.now().isBefore(banUntil);
    }
}
