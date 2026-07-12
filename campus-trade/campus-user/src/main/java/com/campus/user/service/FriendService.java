package com.campus.user.service;

import com.campus.user.dto.FriendRequestVO;
import com.campus.user.dto.FriendStatusVO;
import com.campus.user.dto.FriendVO;

import java.util.List;

public interface FriendService {

    void sendRequest(Long userId, Long peerUserId);

    void acceptRequest(Long userId, Long requestId);

    void rejectRequest(Long userId, Long requestId);

    List<FriendRequestVO> listIncomingRequests(Long userId);

    List<FriendVO> listFriends(Long userId);

    FriendStatusVO getStatus(Long userId, Long peerUserId);

    boolean areFriends(Long userId, Long peerUserId);

    boolean isTradeUnlocked(Long userId, Long peerUserId);

    boolean canMessageUnlimited(Long userId, Long peerUserId);
}
