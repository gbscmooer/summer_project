package com.campus.order.dto;

import com.campus.order.entity.Notification;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知列表 VO，返回给前端。
 */
@Data
public class NotificationVO {
    private Long id;
    private String type;
    private String title;
    private String content;
    private String orderNo;
    private Integer isRead;
    private LocalDateTime createTime;

    public static NotificationVO from(Notification n) {
        NotificationVO vo = new NotificationVO();
        vo.setId(n.getId());
        vo.setType(n.getType());
        vo.setTitle(n.getTitle());
        vo.setContent(n.getContent());
        vo.setOrderNo(n.getOrderNo());
        vo.setIsRead(n.getIsRead());
        vo.setCreateTime(n.getCreateTime());
        return vo;
    }
}
