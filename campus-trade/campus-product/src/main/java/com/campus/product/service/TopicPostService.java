package com.campus.product.service;

import com.campus.common.result.PageResult;
import com.campus.product.dto.TopicPostRequest;
import com.campus.product.dto.TopicPostVO;

public interface TopicPostService {

    PageResult<TopicPostVO> listPosts(Integer pageNum, Integer pageSize);

    TopicPostVO getDetail(Long postId);

    Long createPost(Long userId, TopicPostRequest request);

    void deletePost(Long userId, Long postId);
}
