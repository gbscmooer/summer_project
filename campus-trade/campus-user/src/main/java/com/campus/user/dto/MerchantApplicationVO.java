package com.campus.user.dto;

import com.campus.user.entity.MerchantApplication;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MerchantApplicationVO {
    private Long id;
    private Long userId;
    private String username;
    private String nickname;
    private String shopName;
    private String reason;
    private String contactPhone;
    /** 0-待审核 1-已通过 2-已拒绝 */
    private Integer status;
    private String adminNote;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static MerchantApplicationVO from(MerchantApplication app, String username, String nickname) {
        MerchantApplicationVO vo = new MerchantApplicationVO();
        vo.setId(app.getId());
        vo.setUserId(app.getUserId());
        vo.setUsername(username);
        vo.setNickname(nickname);
        vo.setShopName(app.getShopName());
        vo.setReason(app.getReason());
        vo.setContactPhone(app.getContactPhone());
        vo.setStatus(app.getStatus());
        vo.setAdminNote(app.getAdminNote());
        vo.setCreateTime(app.getCreateTime());
        vo.setUpdateTime(app.getUpdateTime());
        return vo;
    }
}
