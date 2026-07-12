package com.campus.user.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.campus.common.exception.BizException;
import com.campus.common.result.ResultCode;
import com.campus.user.dto.FriendRequestVO;
import com.campus.user.dto.FriendStatusVO;
import com.campus.user.dto.FriendVO;
import com.campus.user.entity.FriendRequest;
import com.campus.user.entity.Friendship;
import com.campus.user.entity.User;
import com.campus.user.mapper.FriendRequestMapper;
import com.campus.user.mapper.FriendshipMapper;
import com.campus.user.mapper.OrderTradeMapper;
import com.campus.user.mapper.UserMapper;
import com.campus.user.service.FriendService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendServiceImpl
        extends ServiceImpl<FriendRequestMapper, FriendRequest>
        implements FriendService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_ACCEPTED = 1;
    private static final int STATUS_REJECTED = 2;

    private final FriendshipMapper friendshipMapper;
    private final OrderTradeMapper orderTradeMapper;
    private final UserMapper userMapper;

    @Override
    public void sendRequest(Long userId, Long peerUserId) {
        if (peerUserId == null) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        if (Objects.equals(userId, peerUserId)) {
            throw new BizException(ResultCode.FRIEND_CANNOT_SELF);
        }
        requireUserExists(peerUserId);
        if (areFriends(userId, peerUserId)) {
            throw new BizException(ResultCode.FRIEND_ALREADY);
        }

        FriendRequest existing = lambdaQuery()
                .eq(FriendRequest::getFromUserId, userId)
                .eq(FriendRequest::getToUserId, peerUserId)
                .one();
        if (existing != null) {
            if (existing.getStatus() != null && existing.getStatus() == STATUS_PENDING) {
                throw new BizException(ResultCode.FRIEND_REQUEST_EXISTS);
            }
            if (existing.getStatus() != null && existing.getStatus() == STATUS_ACCEPTED) {
                throw new BizException(ResultCode.FRIEND_ALREADY);
            }
            // 已拒绝：重新发起
            FriendRequest patch = new FriendRequest();
            patch.setId(existing.getId());
            patch.setStatus(STATUS_PENDING);
            updateById(patch);
            return;
        }

        // 对方已向我发起待处理申请时，直接提示已有申请（避免双向待处理）
        boolean reversePending = lambdaQuery()
                .eq(FriendRequest::getFromUserId, peerUserId)
                .eq(FriendRequest::getToUserId, userId)
                .eq(FriendRequest::getStatus, STATUS_PENDING)
                .exists();
        if (reversePending) {
            throw new BizException(ResultCode.FRIEND_REQUEST_EXISTS);
        }

        FriendRequest request = new FriendRequest();
        request.setFromUserId(userId);
        request.setToUserId(peerUserId);
        request.setStatus(STATUS_PENDING);
        save(request);
    }

    @Override
    @Transactional
    public void acceptRequest(Long userId, Long requestId) {
        FriendRequest request = getById(requestId);
        if (request == null
                || request.getStatus() == null
                || request.getStatus() != STATUS_PENDING
                || !Objects.equals(request.getToUserId(), userId)) {
            throw new BizException(ResultCode.FRIEND_REQUEST_NOT_FOUND);
        }

        ensureFriendship(request.getFromUserId(), request.getToUserId());

        FriendRequest patch = new FriendRequest();
        patch.setId(requestId);
        patch.setStatus(STATUS_ACCEPTED);
        updateById(patch);

        // 若存在反向待处理申请，一并标记为已同意
        FriendRequest reverse = lambdaQuery()
                .eq(FriendRequest::getFromUserId, userId)
                .eq(FriendRequest::getToUserId, request.getFromUserId())
                .eq(FriendRequest::getStatus, STATUS_PENDING)
                .one();
        if (reverse != null) {
            FriendRequest reversePatch = new FriendRequest();
            reversePatch.setId(reverse.getId());
            reversePatch.setStatus(STATUS_ACCEPTED);
            updateById(reversePatch);
        }
    }

    @Override
    public void rejectRequest(Long userId, Long requestId) {
        FriendRequest request = getById(requestId);
        if (request == null
                || request.getStatus() == null
                || request.getStatus() != STATUS_PENDING
                || !Objects.equals(request.getToUserId(), userId)) {
            throw new BizException(ResultCode.FRIEND_REQUEST_NOT_FOUND);
        }
        FriendRequest patch = new FriendRequest();
        patch.setId(requestId);
        patch.setStatus(STATUS_REJECTED);
        updateById(patch);
    }

    @Override
    public List<FriendRequestVO> listIncomingRequests(Long userId) {
        List<FriendRequest> requests = lambdaQuery()
                .eq(FriendRequest::getToUserId, userId)
                .eq(FriendRequest::getStatus, STATUS_PENDING)
                .orderByDesc(FriendRequest::getCreateTime)
                .list();
        if (requests.isEmpty()) {
            return List.of();
        }
        Map<Long, User> userMap = loadUsers(requests.stream()
                .map(FriendRequest::getFromUserId)
                .distinct()
                .collect(Collectors.toList()));
        List<FriendRequestVO> result = new ArrayList<>(requests.size());
        for (FriendRequest req : requests) {
            User from = userMap.get(req.getFromUserId());
            FriendRequestVO vo = new FriendRequestVO();
            vo.setId(req.getId());
            vo.setFromUserId(req.getFromUserId());
            vo.setNickname(from != null ? from.getNickname() : null);
            vo.setAvatar(from != null ? from.getAvatar() : null);
            vo.setStatus(req.getStatus());
            vo.setCreateTime(req.getCreateTime());
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<FriendVO> listFriends(Long userId) {
        List<Friendship> friendships = friendshipMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Friendship>()
                        .and(w -> w.eq(Friendship::getUserLowId, userId)
                                .or()
                                .eq(Friendship::getUserHighId, userId))
                        .orderByDesc(Friendship::getCreateTime));
        if (friendships.isEmpty()) {
            return List.of();
        }
        List<Long> peerIds = friendships.stream()
                .map(f -> Objects.equals(f.getUserLowId(), userId) ? f.getUserHighId() : f.getUserLowId())
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> userMap = loadUsers(peerIds);
        List<FriendVO> result = new ArrayList<>(friendships.size());
        for (Friendship f : friendships) {
            Long peerId = Objects.equals(f.getUserLowId(), userId) ? f.getUserHighId() : f.getUserLowId();
            User peer = userMap.get(peerId);
            FriendVO vo = new FriendVO();
            vo.setUserId(peerId);
            vo.setNickname(peer != null ? peer.getNickname() : null);
            vo.setAvatar(peer != null ? peer.getAvatar() : null);
            vo.setFriendsSince(f.getCreateTime());
            result.add(vo);
        }
        return result;
    }

    @Override
    public FriendStatusVO getStatus(Long userId, Long peerUserId) {
        if (peerUserId == null) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        if (Objects.equals(userId, peerUserId)) {
            throw new BizException(ResultCode.FRIEND_CANNOT_SELF);
        }
        FriendStatusVO vo = new FriendStatusVO();
        boolean friends = areFriends(userId, peerUserId);
        boolean tradeUnlocked = isTradeUnlocked(userId, peerUserId);
        vo.setFriends(friends);
        vo.setTradeUnlocked(tradeUnlocked);
        vo.setCanMessageUnlimited(friends || tradeUnlocked);
        vo.setPendingOutgoing(lambdaQuery()
                .eq(FriendRequest::getFromUserId, userId)
                .eq(FriendRequest::getToUserId, peerUserId)
                .eq(FriendRequest::getStatus, STATUS_PENDING)
                .exists());
        vo.setPendingIncoming(lambdaQuery()
                .eq(FriendRequest::getFromUserId, peerUserId)
                .eq(FriendRequest::getToUserId, userId)
                .eq(FriendRequest::getStatus, STATUS_PENDING)
                .exists());
        return vo;
    }

    @Override
    public boolean areFriends(Long userId, Long peerUserId) {
        if (userId == null || peerUserId == null || Objects.equals(userId, peerUserId)) {
            return false;
        }
        long low = Math.min(userId, peerUserId);
        long high = Math.max(userId, peerUserId);
        return friendshipMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Friendship>()
                        .eq(Friendship::getUserLowId, low)
                        .eq(Friendship::getUserHighId, high)) > 0;
    }

    @Override
    public boolean isTradeUnlocked(Long userId, Long peerUserId) {
        if (userId == null || peerUserId == null || Objects.equals(userId, peerUserId)) {
            return false;
        }
        return orderTradeMapper.countTradeOrders(userId, peerUserId) > 0;
    }

    @Override
    public boolean canMessageUnlimited(Long userId, Long peerUserId) {
        return areFriends(userId, peerUserId) || isTradeUnlocked(userId, peerUserId);
    }

    private void ensureFriendship(Long userA, Long userB) {
        long low = Math.min(userA, userB);
        long high = Math.max(userA, userB);
        boolean exists = friendshipMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Friendship>()
                        .eq(Friendship::getUserLowId, low)
                        .eq(Friendship::getUserHighId, high)) > 0;
        if (exists) {
            return;
        }
        Friendship friendship = new Friendship();
        friendship.setUserLowId(low);
        friendship.setUserHighId(high);
        friendshipMapper.insert(friendship);
    }

    private void requireUserExists(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "用户不存在");
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
