package com.campus.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.campus.common.exception.BizException;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import com.campus.product.dto.TopicCommentRequest;
import com.campus.product.dto.TopicCommentVO;
import com.campus.product.dto.TopicVoteResultVO;
import com.campus.product.entity.TopicComment;
import com.campus.product.entity.TopicCommentVote;
import com.campus.product.entity.TopicPost;
import com.campus.product.feign.UserFeignClient;
import com.campus.product.feign.dto.UserBriefDTO;
import com.campus.product.mapper.TopicCommentMapper;
import com.campus.product.mapper.TopicCommentVoteMapper;
import com.campus.product.mapper.TopicPostMapper;
import com.campus.product.service.TopicCommentService;
import com.campus.product.service.UserPermissionGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicCommentServiceImpl extends ServiceImpl<TopicCommentMapper, TopicComment>
        implements TopicCommentService {

    private static final int MAX_DEPTH = 8;
    private static final String IMAGE_PREFIX = "/api/product/image/";

    private final TopicPostMapper topicPostMapper;
    private final TopicCommentVoteMapper topicCommentVoteMapper;
    private final UserFeignClient userFeignClient;
    private final UserPermissionGuard userPermissionGuard;

    @Override
    public List<TopicCommentVO> listByPost(Long postId, Long viewerUserId) {
        requirePost(postId);
        List<TopicComment> comments = lambdaQuery()
                .eq(TopicComment::getPostId, postId)
                .orderByAsc(TopicComment::getCreateTime)
                .list();
        if (comments.isEmpty()) {
            return List.of();
        }
        List<TopicCommentVO> flat = comments.stream()
                .map(TopicCommentVO::from)
                .collect(Collectors.toList());
        enrichAuthors(flat);
        markUpvoted(flat, viewerUserId);
        return buildTree(flat);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addComment(Long userId, Long postId, TopicCommentRequest request) {
        userPermissionGuard.requireCanComment(userId);
        if (request == null || !StringUtils.hasText(request.getContent())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "请填写评论内容");
        }
        TopicPost post = requirePost(postId);
        Long parentId = request.getParentId();
        if (parentId != null) {
            TopicComment parent = getById(parentId);
            if (parent == null || !postId.equals(parent.getPostId())) {
                throw new BizException(ResultCode.TOPIC_COMMENT_NOT_FOUND);
            }
            if (depthOf(parent) >= MAX_DEPTH) {
                throw new BizException(ResultCode.BAD_REQUEST.getCode(), "回复层级过深");
            }
        }

        String imageUrl = normalizeImageUrl(request.getImageUrl());

        TopicComment comment = new TopicComment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setContent(request.getContent().trim());
        comment.setImageUrl(imageUrl);
        comment.setUpvoteCount(0);
        save(comment);

        topicPostMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<TopicPost>()
                        .eq(TopicPost::getId, postId)
                        .setSql("comment_count = IFNULL(comment_count, 0) + 1"));

        return comment.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TopicVoteResultVO toggleUpvote(Long userId, Long commentId) {
        TopicComment comment = getById(commentId);
        if (comment == null) {
            throw new BizException(ResultCode.TOPIC_COMMENT_NOT_FOUND);
        }
        TopicCommentVote existing = topicCommentVoteMapper.selectOne(
                new LambdaQueryWrapper<TopicCommentVote>()
                        .eq(TopicCommentVote::getCommentId, commentId)
                        .eq(TopicCommentVote::getUserId, userId));
        boolean upvoted;
        if (existing != null) {
            topicCommentVoteMapper.deleteById(existing.getId());
            lambdaUpdate()
                    .eq(TopicComment::getId, commentId)
                    .setSql("upvote_count = GREATEST(IFNULL(upvote_count, 0) - 1, 0)")
                    .update();
            upvoted = false;
        } else {
            TopicCommentVote vote = new TopicCommentVote();
            vote.setCommentId(commentId);
            vote.setUserId(userId);
            topicCommentVoteMapper.insert(vote);
            lambdaUpdate()
                    .eq(TopicComment::getId, commentId)
                    .setSql("upvote_count = IFNULL(upvote_count, 0) + 1")
                    .update();
            upvoted = true;
        }
        TopicComment refreshed = getById(commentId);
        int count = refreshed == null || refreshed.getUpvoteCount() == null ? 0 : refreshed.getUpvoteCount();

        TopicVoteResultVO result = new TopicVoteResultVO();
        result.setUpvoteCount(count);
        result.setUpvoted(upvoted);
        return result;
    }

    private TopicPost requirePost(Long postId) {
        if (postId == null || postId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        TopicPost post = topicPostMapper.selectById(postId);
        if (post == null) {
            throw new BizException(ResultCode.TOPIC_POST_NOT_FOUND);
        }
        return post;
    }

    private int depthOf(TopicComment comment) {
        int depth = 1;
        Long parentId = comment.getParentId();
        Set<Long> seen = new HashSet<>();
        while (parentId != null && depth < MAX_DEPTH + 2) {
            if (!seen.add(parentId)) {
                break;
            }
            TopicComment parent = getById(parentId);
            if (parent == null) {
                break;
            }
            depth++;
            parentId = parent.getParentId();
        }
        return depth;
    }

    private String normalizeImageUrl(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return null;
        }
        String url = imageUrl.trim();
        if (!url.startsWith(IMAGE_PREFIX)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "仅支持本站上传的图片");
        }
        return url;
    }

    private List<TopicCommentVO> buildTree(List<TopicCommentVO> flat) {
        Map<Long, TopicCommentVO> byId = new HashMap<>();
        for (TopicCommentVO vo : flat) {
            byId.put(vo.getCommentId(), vo);
        }
        List<TopicCommentVO> roots = new ArrayList<>();
        for (TopicCommentVO vo : flat) {
            Long parentId = vo.getParentId();
            if (parentId == null) {
                roots.add(vo);
                continue;
            }
            TopicCommentVO parent = byId.get(parentId);
            if (parent == null) {
                roots.add(vo);
            } else {
                parent.getChildren().add(vo);
            }
        }
        return roots;
    }

    private void markUpvoted(List<TopicCommentVO> list, Long viewerUserId) {
        if (viewerUserId == null || list == null || list.isEmpty()) {
            return;
        }
        List<Long> ids = list.stream()
                .map(TopicCommentVO::getCommentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return;
        }
        List<TopicCommentVote> votes = topicCommentVoteMapper.selectList(
                new LambdaQueryWrapper<TopicCommentVote>()
                        .eq(TopicCommentVote::getUserId, viewerUserId)
                        .in(TopicCommentVote::getCommentId, ids));
        Set<Long> voted = votes.stream()
                .map(TopicCommentVote::getCommentId)
                .collect(Collectors.toSet());
        for (TopicCommentVO vo : list) {
            vo.setUpvoted(voted.contains(vo.getCommentId()));
        }
    }

    private void enrichAuthors(List<TopicCommentVO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        String ids = list.stream()
                .map(TopicCommentVO::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        if (!StringUtils.hasText(ids)) {
            return;
        }
        try {
            Result<List<UserBriefDTO>> result = userFeignClient.batchGetUsers(ids);
            if (result == null
                    || result.getCode() == null
                    || result.getCode() != ResultCode.SUCCESS.getCode()
                    || result.getData() == null
                    || result.getData().isEmpty()) {
                return;
            }
            Map<Long, UserBriefDTO> userMap = result.getData().stream()
                    .filter(user -> user.getUserId() != null)
                    .collect(Collectors.toMap(UserBriefDTO::getUserId, user -> user, (a, b) -> a));
            for (TopicCommentVO vo : list) {
                UserBriefDTO user = userMap.get(vo.getUserId());
                if (user != null) {
                    vo.setNickname(user.getNickname());
                    vo.setAvatar(user.getAvatar());
                    vo.setBio(user.getBio());
                }
            }
        } catch (Exception e) {
            log.warn("补全话题评论作者信息失败. ids={}", ids, e);
        }
    }
}
