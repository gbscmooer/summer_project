package com.campus.order.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BizException;
import com.campus.common.result.PageResult;
import com.campus.common.result.ResultCode;
import com.campus.order.dto.NotificationVO;
import com.campus.order.entity.Notification;
import com.campus.order.mapper.NotificationMapper;
import com.campus.order.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
        implements NotificationService {

    @Override
    public PageResult<NotificationVO> listByUser(Long userId, Integer pageNum, Integer pageSize) {
        Page<Notification> page = new Page<>(pageNum, pageSize);
        lambdaQuery()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreateTime)
                .page(page);

        List<NotificationVO> list = page.getRecords().stream()
                .map(NotificationVO::from)
                .collect(Collectors.toList());

        return PageResult.of(page.getTotal(), pageNum, pageSize, list);
    }

    @Override
    public long unreadCount(Long userId) {
        return lambdaQuery()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .count();
    }

    @Override
    public void markRead(Long userId, Long notificationId) {
        Notification n = getById(notificationId);
        if (n == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (!userId.equals(n.getUserId())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        if (n.getIsRead() == 0) {
            n.setIsRead(1);
            updateById(n);
        }
    }

    @Override
    public void markAllRead(Long userId) {
        lambdaUpdate()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1)
                .update();
    }
}
