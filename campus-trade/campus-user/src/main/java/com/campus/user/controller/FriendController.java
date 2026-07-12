package com.campus.user.controller;

import com.campus.common.exception.BizException;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import com.campus.user.dto.FriendRequestVO;
import com.campus.user.dto.FriendStatusVO;
import com.campus.user.dto.FriendVO;
import com.campus.user.dto.PeerUserRequest;
import com.campus.user.service.FriendService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    @PostMapping("/request")
    public Result<Void> sendRequest(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody PeerUserRequest request) {
        requireLogin(userId);
        friendService.sendRequest(userId, request.getPeerUserId());
        return Result.success("好友申请已发送", null);
    }

    @PostMapping("/request/{id}/accept")
    public Result<Void> accept(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id) {
        requireLogin(userId);
        friendService.acceptRequest(userId, id);
        return Result.success("已同意好友申请", null);
    }

    @PostMapping("/request/{id}/reject")
    public Result<Void> reject(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id) {
        requireLogin(userId);
        friendService.rejectRequest(userId, id);
        return Result.success("已拒绝好友申请", null);
    }

    @GetMapping("/requests")
    public Result<List<FriendRequestVO>> listRequests(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireLogin(userId);
        return Result.success(friendService.listIncomingRequests(userId));
    }

    @GetMapping("/list")
    public Result<List<FriendVO>> listFriends(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireLogin(userId);
        return Result.success(friendService.listFriends(userId));
    }

    @GetMapping("/status")
    public Result<FriendStatusVO> status(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam Long peerUserId) {
        requireLogin(userId);
        return Result.success(friendService.getStatus(userId, peerUserId));
    }

    private void requireLogin(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
    }
}
