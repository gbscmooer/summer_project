package com.campus.user.service.impl;

import com.campus.common.constant.UserStatus;
import com.campus.common.exception.BizException;
import com.campus.common.result.ResultCode;
import com.campus.user.dto.PublicUserProfileVO;
import com.campus.user.entity.User;
import com.campus.user.mapper.UserMapper;
import com.campus.user.service.FollowService;
import com.campus.user.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserMapper userMapper;
    private final FollowService followService;

    @Override
    public PublicUserProfileVO getPublicProfile(Long targetUserId, Long viewerUserId) {
        if (targetUserId == null || targetUserId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        User user = userMapper.selectById(targetUserId);
        if (user == null || UserStatus.isEffectivelyBanned(user.getStatus(), user.getBanUntil())) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        int followingCount = followService.countFollowing(targetUserId);
        int followersCount = followService.countFollowers(targetUserId);
        boolean following = viewerUserId != null && followService.isFollowing(viewerUserId, targetUserId);
        return PublicUserProfileVO.from(user, followingCount, followersCount, following);
    }
}
