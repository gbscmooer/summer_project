package com.campus.user.dto;

import com.campus.user.entity.SpecialCertApplication;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SpecialCertApplicationVO {
    private Long id;
    private Long userId;
    private String username;
    private String nickname;
    private String displayName;
    private String reason;
    private String contactPhone;
    /** 0-待审核 1-已通过 2-已拒绝 */
    private Integer status;
    private String adminNote;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static SpecialCertApplicationVO from(SpecialCertApplication app, String username, String nickname) {
        SpecialCertApplicationVO vo = new SpecialCertApplicationVO();
        vo.setId(app.getId());
        vo.setUserId(app.getUserId());
        vo.setUsername(username);
        vo.setNickname(nickname);
        vo.setDisplayName(app.getDisplayName());
        vo.setReason(app.getReason());
        vo.setContactPhone(app.getContactPhone());
        vo.setStatus(app.getStatus());
        vo.setAdminNote(app.getAdminNote());
        vo.setCreateTime(app.getCreateTime());
        vo.setUpdateTime(app.getUpdateTime());
        return vo;
    }
}
