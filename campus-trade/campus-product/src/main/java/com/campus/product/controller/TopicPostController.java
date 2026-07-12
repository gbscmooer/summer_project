package com.campus.product.controller;

import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.product.dto.TopicPostRequest;
import com.campus.product.dto.TopicPostVO;
import com.campus.product.dto.TopicTipRequest;
import com.campus.product.dto.TopicTrendingItemVO;
import com.campus.product.dto.TopicVoteResultVO;
import com.campus.product.service.TopicPostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/topic")
@RequiredArgsConstructor
public class TopicPostController {

    private final TopicPostService topicPostService;

    @GetMapping("/posts/list")
    public Result<PageResult<TopicPostVO>> listPosts(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(topicPostService.listPosts(pageNum, pageSize, keyword));
    }

    @GetMapping("/posts/trending")
    public Result<List<TopicTrendingItemVO>> trending(
            @RequestParam(defaultValue = "8") Integer limit) {
        return Result.success(topicPostService.listTrending(limit));
    }

    @GetMapping("/posts/feed")
    public Result<List<TopicPostVO>> feed(
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(topicPostService.listFeed(size));
    }

    /** 用户主页帖子列表（须在 /posts/{id} 之前，避免路径冲突） */
    @GetMapping("/posts/by-user/{userId}")
    public Result<PageResult<TopicPostVO>> listPostsByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(topicPostService.listPostsByUser(userId, pageNum, pageSize));
    }

    @GetMapping("/posts/{id}")
    public Result<TopicPostVO> getPost(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.success(topicPostService.getDetail(id, userId));
    }

    @PostMapping("/posts")
    public Result<Map<String, Long>> createPost(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody TopicPostRequest request) {
        Long postId = topicPostService.createPost(userId, request);
        return Result.success("发布成功", Map.of("postId", postId));
    }

    @PostMapping("/posts/{id}/delete")
    public Result<Void> deletePost(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        topicPostService.deletePost(userId, id);
        return Result.success("已删除", null);
    }

    @PostMapping("/posts/{id}/upvote")
    public Result<TopicVoteResultVO> upvotePost(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        return Result.success(topicPostService.toggleUpvote(userId, id));
    }

    @PostMapping("/posts/{id}/tip")
    public Result<Map<String, Integer>> tipPost(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id,
            @Valid @RequestBody TopicTipRequest request) {
        Integer tipTotal = topicPostService.tip(userId, id, request.getAmount(), request.getRequestId());
        return Result.success("打赏成功", Map.of("tipTotal", tipTotal));
    }
}
