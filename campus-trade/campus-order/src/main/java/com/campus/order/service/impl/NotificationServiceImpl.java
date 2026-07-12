package com.campus.order.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.campus.common.exception.BizException;
import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import com.campus.common.util.PageParamUtil;
import com.campus.order.dto.AdminBroadcastRequest;
import com.campus.order.dto.AdminBroadcastResult;
import com.campus.order.dto.NotificationVO;
import com.campus.order.entity.Notification;
import com.campus.order.feign.UserFeignClient;
import com.campus.order.mapper.NotificationMapper;
import com.campus.order.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
        implements NotificationService {

    private static final String TYPE_ADMIN_BROADCAST = "ADMIN_BROADCAST";
    private static final int BATCH_SIZE = 200;

    private final UserFeignClient userFeignClient;

    @Override
    public PageResult<NotificationVO> listByUser(Long userId, Integer pageNum, Integer pageSize) {
        int pageNo = PageParamUtil.normalizePageNum(pageNum);
        int size = PageParamUtil.normalizePageSize(pageSize);
        Page<Notification> page = new Page<>(pageNo, size);
        lambdaQuery()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreateTime)
                .page(page);

        List<NotificationVO> list = page.getRecords().stream()
                .map(NotificationVO::from)
                .collect(Collectors.toList());

        return PageResult.of(page.getTotal(), pageNo, size, list);
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

    @Override
    public AdminBroadcastResult broadcast(Long adminId, AdminBroadcastRequest request) {
        if (request == null || !StringUtils.hasText(request.getTargetType())) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        String targetType = request.getTargetType().trim().toUpperCase();
        List<Long> recipientIds = resolveRecipients(targetType, request);
        if (recipientIds.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "未找到可发送的通知接收人");
        }

        String title = request.getTitle().trim();
        String content = request.getContent().trim();
        List<Notification> rows = recipientIds.stream().map(userId -> {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setType(TYPE_ADMIN_BROADCAST);
            notification.setTitle(title);
            notification.setContent(content);
            notification.setOrderNo(null);
            notification.setIsRead(0);
            return notification;
        }).collect(Collectors.toList());

        for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, rows.size());
            saveBatch(rows.subList(i, end));
        }
        log.info("Admin broadcast sent by adminId={}, targetType={}, recipients={}",
                adminId, targetType, recipientIds.size());
        return AdminBroadcastResult.builder()
                .recipientCount(recipientIds.size())
                .targetType(targetType)
                .build();
    }

    private List<Long> resolveRecipients(String targetType, AdminBroadcastRequest request) {
        if ("ALL".equals(targetType)) {
            return unwrap(userFeignClient.listAllUserIds());
        }
        if ("SPECIFIC".equals(targetType)) {
            Set<Long> ids = new LinkedHashSet<>();
            if (request.getUserIds() != null) {
                request.getUserIds().stream()
                        .filter(id -> id != null && id > 0)
                        .forEach(ids::add);
            }
            if (request.getUsernames() != null && !request.getUsernames().isEmpty()) {
                String joined = request.getUsernames().stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .collect(Collectors.joining(","));
                if (StringUtils.hasText(joined)) {
                    ids.addAll(unwrap(userFeignClient.resolveUserIdsByUsernames(joined)));
                }
            }
            return new ArrayList<>(ids);
        }
        throw new BizException(ResultCode.BAD_REQUEST.getCode(), "targetType 仅支持 ALL 或 SPECIFIC");
    }

    private <T> T unwrap(Result<T> result) {
        if (result == null || result.getCode() != ResultCode.SUCCESS.getCode()) {
            throw new BizException(ResultCode.INTERNAL_ERROR);
        }
        return result.getData();
    }
}
