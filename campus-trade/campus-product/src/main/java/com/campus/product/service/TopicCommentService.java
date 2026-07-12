package com.campus.product.service;

import com.campus.product.dto.TopicCommentRequest;
import com.campus.product.dto.TopicCommentVO;
import com.campus.product.dto.TopicVoteResultVO;

import java.util.List;

public interface TopicCommentService {

    List<TopicCommentVO> listByPost(Long postId, Long viewerUserId);

    Long addComment(Long userId, Long postId, TopicCommentRequest request);

    TopicVoteResultVO toggleUpvote(Long userId, Long commentId);
}
