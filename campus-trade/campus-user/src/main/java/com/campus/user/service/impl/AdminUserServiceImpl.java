package com.campus.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.constant.UserCapability;
import com.campus.common.constant.UserRole;
import com.campus.common.constant.UserStatus;
import com.campus.common.dto.UserPermissionsVO;
import com.campus.common.exception.BizException;
import com.campus.common.result.PageResult;
import com.campus.common.result.ResultCode;
import com.campus.common.util.PageParamUtil;
import com.campus.user.dto.AdminUserVO;
import com.campus.user.dto.BanUserRequest;
import com.campus.user.dto.UpdateUserPermissionsRequest;
import com.campus.user.entity.User;
import com.campus.user.mapper.UserMapper;
import com.campus.user.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserMapper userMapper;

    @Override
    public PageResult<AdminUserVO> listUsers(Integer pageNum, Integer pageSize, String keyword) {
        int pageNo = PageParamUtil.normalizePageNum(pageNum);
        int size = PageParamUtil.normalizePageSize(pageSize);
        Page<User> page = new Page<>(pageNo, size);
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;

        userMapper.selectPage(page, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .and(StringUtils.hasText(kw), w -> w
                        .like(User::getUsername, kw)
                        .or()
                        .like(User::getNickname, kw))
                .orderByDesc(User::getCreateTime));

        List<AdminUserVO> list = page.getRecords().stream()
                .peek(this::expireBanIfNeeded)
                .map(AdminUserVO::from)
                .collect(Collectors.toList());
        return PageResult.of(page.getTotal(), pageNo, size, list);
    }

    @Override
    public void banUser(Long adminId, Long targetUserId, BanUserRequest request) {
        if (request == null || !StringUtils.hasText(request.getReason())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "请填写封禁原因");
        }
        User target = requireTarget(targetUserId);
        expireBanIfNeeded(target);
        if (adminId.equals(targetUserId)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "不能封禁自己的账号");
        }
        if (target.getRole() != null && target.getRole() == UserRole.ADMIN) {
            throw new BizException(ResultCode.CANNOT_BAN_ADMIN);
        }
        if (UserStatus.isEffectivelyBanned(target.getStatus(), target.getBanUntil())) {
            return;
        }

        String reason = request.getReason().trim();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime banUntil = null;
        Integer durationDays = request.getDurationDays();
        if (durationDays != null && durationDays > 0) {
            banUntil = now.plusDays(durationDays);
        }

        User patch = new User();
        patch.setId(targetUserId);
        patch.setStatus(UserStatus.BANNED);
        patch.setBanReason(reason);
        patch.setBanUntil(banUntil);
        patch.setBannedBy(adminId);
        patch.setBannedAt(now);
        userMapper.updateById(patch);
    }

    @Override
    public void unbanUser(Long adminId, Long targetUserId) {
        User target = requireTarget(targetUserId);
        if (!UserStatus.isEffectivelyBanned(target.getStatus(), target.getBanUntil())) {
            clearBan(targetUserId);
            return;
        }
        clearBan(targetUserId);
    }

    @Override
    public UserPermissionsVO updatePermissions(Long adminId, Long targetUserId, UpdateUserPermissionsRequest request) {
        if (request == null
                || request.getCanPost() == null
                || request.getCanComment() == null
                || request.getCanOrder() == null
                || request.getCanBroadcast() == null) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        requireTarget(targetUserId);

        User patch = new User();
        patch.setId(targetUserId);
        patch.setPermPost(UserCapability.toFlag(request.getCanPost()));
        patch.setPermComment(UserCapability.toFlag(request.getCanComment()));
        patch.setPermOrder(UserCapability.toFlag(request.getCanOrder()));
        patch.setPermBroadcast(UserCapability.toFlag(request.getCanBroadcast()));
        userMapper.updateById(patch);

        return UserPermissionsVO.fromFlags(
                patch.getPermPost(),
                patch.getPermComment(),
                patch.getPermOrder(),
                patch.getPermBroadcast());
    }

    private User requireTarget(Long targetUserId) {
        if (targetUserId == null || targetUserId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        User target = userMapper.selectById(targetUserId);
        if (target == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return target;
    }

    private void expireBanIfNeeded(User user) {
        if (user == null || user.getId() == null) {
            return;
        }
        if (!UserStatus.isBanned(user.getStatus())) {
            return;
        }
        if (user.getBanUntil() != null && !LocalDateTime.now().isBefore(user.getBanUntil())) {
            clearBan(user.getId());
            user.setStatus(UserStatus.ACTIVE);
            user.setBanReason(null);
            user.setBanUntil(null);
            user.setBannedBy(null);
            user.setBannedAt(null);
        }
    }

    private void clearBan(Long userId) {
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getStatus, UserStatus.ACTIVE)
                .set(User::getBanReason, null)
                .set(User::getBanUntil, null)
                .set(User::getBannedBy, null)
                .set(User::getBannedAt, null));
    }
}
