package com.campus.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.campus.common.exception.BizException;
import com.campus.common.result.PageResult;
import com.campus.common.result.ResultCode;
import com.campus.common.util.PageParamUtil;
import com.campus.user.dto.FollowStatusVO;
import com.campus.user.dto.FollowUserVO;
import com.campus.user.dto.UserProfileVO;
import com.campus.user.entity.User;
import com.campus.user.entity.UserFollow;
import com.campus.user.mapper.UserFollowMapper;
import com.campus.user.mapper.UserMapper;
import com.campus.user.service.UserFollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserFollowServiceImpl
        extends ServiceImpl<UserFollowMapper, UserFollow>
        implements UserFollowService {

    private final UserMapper userMapper;

    @Override
    public UserProfileVO getProfile(Long userId, Long viewerUserId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }

        UserProfileVO vo = new UserProfileVO();
        vo.setUserId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setBio(user.getBio() == null ? "" : user.getBio());
        vo.setRole(user.getRole() == null ? 0 : user.getRole());
        vo.setCreateTime(user.getCreateTime());
        vo.setFollowingCount(countFollowing(userId));
        vo.setFollowerCount(countFollowers(userId));
        vo.setPostCount(0L);

        boolean self = viewerUserId != null && Objects.equals(viewerUserId, userId);
        vo.setSelf(self);
        if (viewerUserId == null || self) {
            vo.setFollowing(false);
        } else {
            vo.setFollowing(isFollowing(viewerUserId, userId));
        }
        return vo;
    }

    @Override
    public void follow(Long followerId, Long followeeId) {
        if (followeeId == null || followeeId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        if (Objects.equals(followerId, followeeId)) {
            throw new BizException(ResultCode.FOLLOW_SELF);
        }
        requireUserExists(followeeId);
        if (isFollowing(followerId, followeeId)) {
            throw new BizException(ResultCode.FOLLOW_ALREADY);
        }

        UserFollow follow = new UserFollow();
        follow.setFollowerId(followerId);
        follow.setFolloweeId(followeeId);
        try {
            save(follow);
        } catch (DataIntegrityViolationException e) {
            throw new BizException(ResultCode.FOLLOW_ALREADY);
        }
    }

    @Override
    public void unfollow(Long followerId, Long followeeId) {
        if (followeeId == null || followeeId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        if (Objects.equals(followerId, followeeId)) {
            throw new BizException(ResultCode.FOLLOW_SELF);
        }
        UserFollow existing = getOne(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, followeeId));
        if (existing == null) {
            throw new BizException(ResultCode.FOLLOW_NOT_FOUND);
        }
        removeById(existing.getId());
    }

    @Override
    public FollowStatusVO getStatus(Long followerId, Long peerUserId) {
        if (peerUserId == null || peerUserId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        if (Objects.equals(followerId, peerUserId)) {
            return new FollowStatusVO(false);
        }
        return new FollowStatusVO(isFollowing(followerId, peerUserId));
    }

    @Override
    public PageResult<FollowUserVO> listFollowers(Long userId, Integer pageNum, Integer pageSize) {
        requireUserExists(userId);
        int pageNo = PageParamUtil.normalizePageNum(pageNum);
        int size = PageParamUtil.normalizePageSize(pageSize);
        Page<UserFollow> page = new Page<>(pageNo, size);
        lambdaQuery()
                .eq(UserFollow::getFolloweeId, userId)
                .orderByDesc(UserFollow::getCreateTime)
                .page(page);
        return PageResult.of(page.getTotal(), pageNo, size, toFollowUserVOs(page.getRecords(), true));
    }

    @Override
    public PageResult<FollowUserVO> listFollowing(Long userId, Integer pageNum, Integer pageSize) {
        requireUserExists(userId);
        int pageNo = PageParamUtil.normalizePageNum(pageNum);
        int size = PageParamUtil.normalizePageSize(pageSize);
        Page<UserFollow> page = new Page<>(pageNo, size);
        lambdaQuery()
                .eq(UserFollow::getFollowerId, userId)
                .orderByDesc(UserFollow::getCreateTime)
                .page(page);
        return PageResult.of(page.getTotal(), pageNo, size, toFollowUserVOs(page.getRecords(), false));
    }

    @Override
    public long countFollowing(Long userId) {
        return count(new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getFollowerId, userId));
    }

    @Override
    public long countFollowers(Long userId) {
        return count(new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getFolloweeId, userId));
    }

    @Override
    public boolean isFollowing(Long followerId, Long followeeId) {
        if (followerId == null || followeeId == null) {
            return false;
        }
        return lambdaQuery()
                .eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, followeeId)
                .exists();
    }

    private List<FollowUserVO> toFollowUserVOs(List<UserFollow> records, boolean asFollowers) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = records.stream()
                .map(f -> asFollowers ? f.getFollowerId() : f.getFolloweeId())
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> userMap = loadUsers(userIds);
        List<FollowUserVO> result = new ArrayList<>(records.size());
        for (UserFollow follow : records) {
            Long peerId = asFollowers ? follow.getFollowerId() : follow.getFolloweeId();
            User peer = userMap.get(peerId);
            FollowUserVO vo = new FollowUserVO();
            vo.setUserId(peerId);
            vo.setNickname(peer != null ? peer.getNickname() : null);
            vo.setAvatar(peer != null ? peer.getAvatar() : null);
            vo.setBio(peer != null ? (peer.getBio() == null ? "" : peer.getBio()) : null);
            vo.setFollowTime(follow.getCreateTime());
            result.add(vo);
        }
        return result;
    }

    private void requireUserExists(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
    }

    private Map<Long, User> loadUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
    }
}
