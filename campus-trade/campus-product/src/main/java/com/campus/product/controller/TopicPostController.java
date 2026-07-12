package com.campus.product.controller;

import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.product.dto.TopicPostRequest;
import com.campus.product.dto.TopicPostVO;
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

import java.util.Map;

@RestController
@RequestMapping("/topic")
@RequiredArgsConstructor
public class TopicPostController {

    private final TopicPostService topicPostService;

    @GetMapping("/posts/list")
    public Result<PageResult<TopicPostVO>> listPosts(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(topicPostService.listPosts(pageNum, pageSize));
    }

    @GetMapping("/posts/{id}")
    public Result<TopicPostVO> getPost(@PathVariable Long id) {
        return Result.success(topicPostService.getDetail(id));
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
}
