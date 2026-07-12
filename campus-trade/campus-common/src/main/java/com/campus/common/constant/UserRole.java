package com.campus.common.constant;

/**
 * 用户角色常量，对应 t_user.role。
 */
public final class UserRole {

    /** 个人账户（默认注册角色，不可发布商品） */
    public static final int USER = 0;
    /** 管理员 */
    public static final int ADMIN = 1;
    /** 商家（经管理员审核后由个人账户升级） */
    public static final int MERCHANT = 2;

    private UserRole() {
    }

    public static boolean isMerchant(int role) {
        return role == MERCHANT;
    }

    public static boolean isAdmin(int role) {
        return role == ADMIN;
    }

    /** 商家或管理员可发布商品。 */
    public static boolean canPublish(int role) {
        return role == ADMIN || role == MERCHANT;
    }

    /** @deprecated 使用 {@link #canPublish(int)} */
    public static boolean hasUnlimitedPublish(int role) {
        return canPublish(role);
    }
}
