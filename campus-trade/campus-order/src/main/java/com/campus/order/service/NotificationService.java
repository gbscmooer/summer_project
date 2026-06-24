package com.campus.order.service;

import com.campus.common.result.PageResult;
import com.campus.order.dto.NotificationVO;

public interface NotificationService {

    /** 分页查询当前用户的通知（最新在前）。 */
    PageResult<NotificationVO> listByUser(Long userId, Integer pageNum, Integer pageSize);

    /** 当前用户的未读通知数。 */
    long unreadCount(Long userId);

    /** 将指定通知标记为已读（需校验归属权）。 */
    void markRead(Long userId, Long notificationId);

    /** 将当前用户所有通知一键标记已读。 */
    void markAllRead(Long userId);
}
