package com.campus.user.service;

import com.campus.common.result.PageResult;
import com.campus.user.dto.FollowStatusVO;
import com.campus.user.dto.FollowUserVO;
import com.campus.user.dto.UserProfileVO;

public interface UserFollowService {

    UserProfileVO getProfile(Long userId, Long viewerUserId);

    void follow(Long followerId, Long followeeId);

    void unfollow(Long followerId, Long followeeId);

    FollowStatusVO getStatus(Long followerId, Long peerUserId);

    PageResult<FollowUserVO> listFollowers(Long userId, Integer pageNum, Integer pageSize);

    PageResult<FollowUserVO> listFollowing(Long userId, Integer pageNum, Integer pageSize);

    long countFollowing(Long userId);

    long countFollowers(Long userId);

    boolean isFollowing(Long followerId, Long followeeId);
}
