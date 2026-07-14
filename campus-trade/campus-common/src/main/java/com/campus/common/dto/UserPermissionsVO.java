package com.campus.common.dto;

import com.campus.common.constant.UserCapability;
import lombok.Data;

/**
 * 用户细粒度权限快照，供管理端展示与跨服务鉴权。
 */
@Data
public class UserPermissionsVO {
    /** 能否发帖（话题） */
    private boolean canPost = true;
    /** 能否发言留言（商品留言 / 话题评论 / 私信） */
    private boolean canComment = true;
    /** 能否下单 */
    private boolean canOrder = true;
    /** 能否广播系统通知（管理后台发送通知） */
    private boolean canBroadcast = true;

    public static UserPermissionsVO allAllowed() {
        return new UserPermissionsVO();
    }

    public static UserPermissionsVO fromFlags(
            Integer permPost,
            Integer permComment,
            Integer permOrder,
            Integer permBroadcast) {
        UserPermissionsVO vo = new UserPermissionsVO();
        vo.setCanPost(UserCapability.isAllowed(permPost));
        vo.setCanComment(UserCapability.isAllowed(permComment));
        vo.setCanOrder(UserCapability.isAllowed(permOrder));
        vo.setCanBroadcast(UserCapability.isAllowed(permBroadcast));
        return vo;
    }
}
