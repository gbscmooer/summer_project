package com.campus.product.controller;

import com.campus.common.result.Result;
import com.campus.product.dto.TopicCommentRequest;
import com.campus.product.dto.TopicCommentVO;
import com.campus.product.dto.TopicVoteResultVO;
import com.campus.product.service.TopicCommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/topic")
@RequiredArgsConstructor
public class TopicCommentController {

    private final TopicCommentService topicCommentService;

    @GetMapping("/posts/{postId}/comments")
    public Result<List<TopicCommentVO>> listComments(
            @PathVariable Long postId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.success(topicCommentService.listByPost(postId, userId));
    }

    @PostMapping("/posts/{postId}/comments")
    public Result<Map<String, Long>> addComment(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long postId,
            @Valid @RequestBody TopicCommentRequest request) {
        Long commentId = topicCommentService.addComment(userId, postId, request);
        return Result.success("评论成功", Map.of("commentId", commentId));
    }

    @PostMapping("/comments/{commentId}/upvote")
    public Result<TopicVoteResultVO> upvoteComment(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long commentId) {
        return Result.success(topicCommentService.toggleUpvote(userId, commentId));
    }
}
