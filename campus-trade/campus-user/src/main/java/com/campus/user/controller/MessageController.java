package com.campus.user.controller;

import com.campus.common.exception.BizException;
import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import com.campus.user.dto.ConversationVO;
import com.campus.user.dto.MessageVO;
import com.campus.user.dto.PeerUserRequest;
import com.campus.user.dto.SendMessageRequest;
import com.campus.user.dto.UnreadCountVO;
import com.campus.user.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping("/conversations")
    public Result<ConversationVO> createConversation(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody PeerUserRequest request) {
        requireLogin(userId);
        return Result.success(messageService.getOrCreateConversation(userId, request.getPeerUserId()));
    }

    @GetMapping("/conversations")
    public Result<List<ConversationVO>> listConversations(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireLogin(userId);
        return Result.success(messageService.listConversations(userId));
    }

    @GetMapping("/conversations/{id}/messages")
    public Result<PageResult<MessageVO>> listMessages(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        requireLogin(userId);
        return Result.success(messageService.listMessages(userId, id, pageNum, pageSize));
    }

    @PostMapping("/conversations/{id}/messages")
    public Result<MessageVO> sendMessage(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id,
            @Valid @RequestBody SendMessageRequest request) {
        requireLogin(userId);
        return Result.success(messageService.sendMessage(userId, id, request.getContent()));
    }

    @PostMapping("/conversations/{id}/read")
    public Result<Void> markRead(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id) {
        requireLogin(userId);
        messageService.markRead(userId, id);
        return Result.success();
    }

    @GetMapping("/messages/unread-count")
    public Result<UnreadCountVO> unreadCount(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireLogin(userId);
        return Result.success(messageService.unreadCount(userId));
    }

    private void requireLogin(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
    }
}
