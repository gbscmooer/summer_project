package com.campus.user.service;

import com.campus.user.dto.PublicUserProfileVO;

public interface ProfileService {
    PublicUserProfileVO getPublicProfile(Long targetUserId, Long viewerUserId);
}
