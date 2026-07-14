package com.campus.user.dto;

import com.campus.common.constant.UserRole;
import com.campus.common.constant.UserStatus;
import com.campus.user.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminUserVO {
    private Long userId;
    private String username;
    private String nickname;
    private Integer role;
    private String roleLabel;
    private Integer status;
    private String banReason;
    private LocalDateTime banUntil;
    private LocalDateTime bannedAt;
    private LocalDateTime createTime;

    public static AdminUserVO from(User user) {
        AdminUserVO vo = new AdminUserVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        int role = user.getRole() == null ? UserRole.USER : user.getRole();
        vo.setRole(role);
        vo.setRoleLabel(roleLabel(role));
        int status = user.getStatus() == null ? 0 : user.getStatus();
        if (status == 1 && !UserStatus.isEffectivelyBanned(status, user.getBanUntil())) {
            status = 0;
        }
        vo.setStatus(status);
        vo.setBanReason(user.getBanReason());
        vo.setBanUntil(user.getBanUntil());
        vo.setBannedAt(user.getBannedAt());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }

    private static String roleLabel(int role) {
        if (role == UserRole.ADMIN) {
            return "管理员";
        }
        if (role == UserRole.MERCHANT) {
            return "商家";
        }
        if (role == UserRole.OFFICIAL) {
            return "特殊认证";
        }
        return "个人账户";
    }
}
