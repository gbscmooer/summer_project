package com.campus.user.service;

public interface FollowService {
    void follow(Long followerId, Long followeeId);

    void unfollow(Long followerId, Long followeeId);

    boolean isFollowing(Long followerId, Long followeeId);

    int countFollowing(Long userId);

    int countFollowers(Long userId);
}
