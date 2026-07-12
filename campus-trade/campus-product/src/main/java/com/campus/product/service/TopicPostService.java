package com.campus.product.service;

import com.campus.common.result.PageResult;
import com.campus.product.dto.TopicPostRequest;
import com.campus.product.dto.TopicPostVO;
import com.campus.product.dto.TopicTrendingItemVO;
import com.campus.product.dto.TopicVoteResultVO;

import java.util.List;

public interface TopicPostService {

    PageResult<TopicPostVO> listPosts(Integer pageNum, Integer pageSize, String keyword);

    /** 按用户分页查询其发布的话题帖（content 为预览）。 */
    PageResult<TopicPostVO> listPostsByUser(Long userId, Integer pageNum, Integer pageSize);

    List<TopicTrendingItemVO> listTrending(Integer limit);

    TopicPostVO getDetail(Long postId, Long viewerUserId);

    Long createPost(Long userId, TopicPostRequest request);

    void deletePost(Long userId, Long postId);

    TopicVoteResultVO toggleUpvote(Long userId, Long postId);

    /** 打赏帖子，返回更新后的 tipTotal。 */
    Integer tip(Long tipperId, Long postId, Integer amount, String requestId);

    /** 首页随机 Feed。 */
    List<TopicPostVO> listFeed(Integer size);
}
