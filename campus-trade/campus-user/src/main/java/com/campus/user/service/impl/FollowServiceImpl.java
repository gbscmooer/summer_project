package com.campus.user.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.campus.common.exception.BizException;
import com.campus.common.result.ResultCode;
import com.campus.user.entity.UserFollow;
import com.campus.user.mapper.UserFollowMapper;
import com.campus.user.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FollowServiceImpl extends ServiceImpl<UserFollowMapper, UserFollow> implements FollowService {

    @Override
    public void follow(Long followerId, Long followeeId) {
        if (followerId == null || followeeId == null) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        if (followerId.equals(followeeId)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "不能关注自己");
        }
        boolean exists = lambdaQuery()
                .eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, followeeId)
                .exists();
        if (exists) {
            return;
        }
        UserFollow row = new UserFollow();
        row.setFollowerId(followerId);
        row.setFolloweeId(followeeId);
        save(row);
    }

    @Override
    public void unfollow(Long followerId, Long followeeId) {
        if (followerId == null || followeeId == null) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        lambdaUpdate()
                .eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, followeeId)
                .remove();
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

    @Override
    public int countFollowing(Long userId) {
        if (userId == null) {
            return 0;
        }
        return Math.toIntExact(lambdaQuery().eq(UserFollow::getFollowerId, userId).count());
    }

    @Override
    public int countFollowers(Long userId) {
        if (userId == null) {
            return 0;
        }
        return Math.toIntExact(lambdaQuery().eq(UserFollow::getFolloweeId, userId).count());
    }
}
