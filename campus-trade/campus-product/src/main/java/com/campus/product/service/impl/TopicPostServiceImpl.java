package com.campus.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.campus.common.exception.BizException;
import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import com.campus.common.util.PageParamUtil;
import com.campus.product.dto.ProductListVO;
import com.campus.product.dto.TopicPostRequest;
import com.campus.product.dto.TopicPostVO;
import com.campus.product.dto.TopicTrendingItemVO;
import com.campus.product.dto.TopicVoteResultVO;
import com.campus.product.entity.Product;
import com.campus.product.entity.TopicComment;
import com.campus.product.entity.TopicPost;
import com.campus.product.entity.TopicPostProduct;
import com.campus.product.entity.TopicPostVote;
import com.campus.product.entity.TopicTipReceipt;
import com.campus.product.feign.UserFeignClient;
import com.campus.product.feign.dto.PointsTransferRequest;
import com.campus.product.feign.dto.UserBriefDTO;
import com.campus.product.mapper.ProductMapper;
import com.campus.product.mapper.TopicCommentMapper;
import com.campus.product.mapper.TopicPostMapper;
import com.campus.product.mapper.TopicPostProductMapper;
import com.campus.product.mapper.TopicPostVoteMapper;
import com.campus.product.mapper.TopicTipReceiptMapper;
import com.campus.product.service.TopicPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicPostServiceImpl extends ServiceImpl<TopicPostMapper, TopicPost> implements TopicPostService {

    private static final int CONTENT_PREVIEW_LEN = 160;
    private static final int MAX_PRODUCTS = 5;
    private static final int DEFAULT_TRENDING_LIMIT = 8;
    private static final int MAX_TRENDING_LIMIT = 20;

    private final TopicPostProductMapper topicPostProductMapper;
    private final TopicPostVoteMapper topicPostVoteMapper;
    private final TopicTipReceiptMapper topicTipReceiptMapper;
    private final TopicCommentMapper topicCommentMapper;
    private final ProductMapper productMapper;
    private final UserFeignClient userFeignClient;

    @Override
    public PageResult<TopicPostVO> listPosts(Integer pageNum, Integer pageSize, String keyword) {
        int pageNo = PageParamUtil.normalizePageNum(pageNum);
        int size = PageParamUtil.normalizePageSize(pageSize);
        Page<TopicPost> page = new Page<>(pageNo, size);
        var query = lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            query.and(q -> q.like(TopicPost::getTitle, kw).or().like(TopicPost::getContent, kw));
        }
        query.orderByDesc(TopicPost::getCreateTime).page(page);

        List<TopicPostVO> list = page.getRecords().stream()
                .map(post -> {
                    TopicPostVO vo = TopicPostVO.from(post);
                    vo.setContent(previewContent(post.getContent()));
                    return vo;
                })
                .collect(Collectors.toList());
        enrichAuthors(list);
        enrichProductCounts(list);
        return PageResult.of(page.getTotal(), pageNo, size, list);
    }

    @Override
    public PageResult<TopicPostVO> listPostsByUser(Long userId, Integer pageNum, Integer pageSize) {
        if (userId == null || userId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        int pageNo = PageParamUtil.normalizePageNum(pageNum);
        int size = PageParamUtil.normalizePageSize(pageSize);
        Page<TopicPost> page = new Page<>(pageNo, size);
        lambdaQuery()
                .eq(TopicPost::getUserId, userId)
                .orderByDesc(TopicPost::getCreateTime)
                .page(page);

        List<TopicPostVO> list = page.getRecords().stream()
                .map(post -> {
                    TopicPostVO vo = TopicPostVO.from(post);
                    vo.setContent(previewContent(post.getContent()));
                    return vo;
                })
                .collect(Collectors.toList());
        enrichAuthors(list);
        enrichProductCounts(list);
        return PageResult.of(page.getTotal(), pageNo, size, list);
    }

    @Override
    public List<TopicTrendingItemVO> listTrending(Integer limit) {
        int size = limit == null || limit <= 0 ? DEFAULT_TRENDING_LIMIT : Math.min(limit, MAX_TRENDING_LIMIT);
        List<TopicPost> posts = lambdaQuery()
                .orderByDesc(TopicPost::getCreateTime)
                .last("LIMIT 50")
                .list();
        LocalDateTime now = LocalDateTime.now();
        List<TopicTrendingItemVO> scored = new ArrayList<>();
        for (TopicPost post : posts) {
            int comments = post.getCommentCount() == null ? 0 : post.getCommentCount();
            int upvotes = post.getUpvoteCount() == null ? 0 : post.getUpvoteCount();
            double hours = 2.0;
            if (post.getCreateTime() != null) {
                hours = Math.max(2.0, Duration.between(post.getCreateTime(), now).toMinutes() / 60.0);
            }
            double heat = (comments * 3.0 + upvotes * 2.0 + 1.0) / Math.pow(hours, 1.2);
            TopicTrendingItemVO item = new TopicTrendingItemVO();
            item.setPostId(post.getId());
            item.setTitle(post.getTitle());
            item.setCommentCount(comments);
            item.setUpvoteCount(upvotes);
            item.setHeatScore(heat);
            item.setReason(buildTrendingReason(comments, upvotes));
            scored.add(item);
        }
        scored.sort(Comparator.comparing(TopicTrendingItemVO::getHeatScore).reversed());
        if (scored.size() > size) {
            return scored.subList(0, size);
        }
        return scored;
    }

    @Override
    public TopicPostVO getDetail(Long postId, Long viewerUserId) {
        TopicPost post = requirePost(postId);
        TopicPostVO vo = TopicPostVO.from(post);
        vo.setProducts(loadAttachedProducts(postId));
        vo.setProductCount(vo.getProducts() == null ? 0 : vo.getProducts().size());
        enrichAuthors(Collections.singletonList(vo));
        if (viewerUserId != null) {
            TopicPostVote vote = topicPostVoteMapper.selectOne(
                    new LambdaQueryWrapper<TopicPostVote>()
                            .eq(TopicPostVote::getPostId, postId)
                            .eq(TopicPostVote::getUserId, viewerUserId));
            vo.setUpvoted(vote != null);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPost(Long userId, TopicPostRequest request) {
        if (request == null || !StringUtils.hasText(request.getTitle())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "请填写标题");
        }
        List<Long> productIds = normalizeProductIds(request.getProductIds());
        validateOwnedProducts(userId, productIds);

        String content = request.getContent() == null ? "" : request.getContent().trim();

        TopicPost post = new TopicPost();
        post.setUserId(userId);
        post.setTitle(request.getTitle().trim());
        post.setContent(content);
        post.setUpvoteCount(0);
        post.setCommentCount(0);
        post.setTipTotal(0);
        post.setTipEnabled(Boolean.TRUE.equals(request.getTipEnabled()) ? 1 : 0);
        save(post);

        for (Long productId : productIds) {
            TopicPostProduct link = new TopicPostProduct();
            link.setPostId(post.getId());
            link.setProductId(productId);
            topicPostProductMapper.insert(link);
        }
        return post.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(Long userId, Long postId) {
        TopicPost post = requirePost(postId);
        if (!post.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        topicPostProductMapper.delete(new LambdaQueryWrapper<TopicPostProduct>()
                .eq(TopicPostProduct::getPostId, postId));
        topicPostVoteMapper.delete(new LambdaQueryWrapper<TopicPostVote>()
                .eq(TopicPostVote::getPostId, postId));
        topicCommentMapper.delete(new LambdaQueryWrapper<TopicComment>()
                .eq(TopicComment::getPostId, postId));
        removeById(postId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TopicVoteResultVO toggleUpvote(Long userId, Long postId) {
        requirePost(postId);
        TopicPostVote existing = topicPostVoteMapper.selectOne(
                new LambdaQueryWrapper<TopicPostVote>()
                        .eq(TopicPostVote::getPostId, postId)
                        .eq(TopicPostVote::getUserId, userId));
        boolean upvoted;
        if (existing != null) {
            topicPostVoteMapper.deleteById(existing.getId());
            lambdaUpdate()
                    .eq(TopicPost::getId, postId)
                    .setSql("upvote_count = GREATEST(IFNULL(upvote_count, 0) - 1, 0)")
                    .update();
            upvoted = false;
        } else {
            TopicPostVote vote = new TopicPostVote();
            vote.setPostId(postId);
            vote.setUserId(userId);
            topicPostVoteMapper.insert(vote);
            lambdaUpdate()
                    .eq(TopicPost::getId, postId)
                    .setSql("upvote_count = IFNULL(upvote_count, 0) + 1")
                    .update();
            upvoted = true;
        }
        TopicPost refreshed = getById(postId);
        int count = refreshed == null || refreshed.getUpvoteCount() == null ? 0 : refreshed.getUpvoteCount();

        TopicVoteResultVO result = new TopicVoteResultVO();
        result.setUpvoteCount(count);
        result.setUpvoted(upvoted);
        if (upvoted) {
            notifyLikeQuest(userId, postId);
        } else {
            notifyUnlikeQuest(userId, postId);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer tip(Long tipperId, Long postId, Integer amount, String requestId) {
        if (amount == null || amount <= 0) {
            throw new BizException(ResultCode.TOPIC_TIP_INVALID);
        }
        TopicPost post = requirePost(postId);
        if (post.getTipEnabled() == null || post.getTipEnabled() != 1) {
            throw new BizException(ResultCode.TOPIC_TIP_DISABLED);
        }
        if (tipperId.equals(post.getUserId())) {
            throw new BizException(ResultCode.TOPIC_TIP_SELF);
        }

        String idempotencyKey = (requestId != null && !requestId.isBlank())
                ? requestId.trim()
                : UUID.randomUUID().toString();

        // 本地收据：保证积分划转成功后 tip_total 可在重试时补齐，且不会重复累加
        TopicTipReceipt existing = topicTipReceiptMapper.selectOne(
                new LambdaQueryWrapper<TopicTipReceipt>()
                        .eq(TopicTipReceipt::getRequestId, idempotencyKey)
                        .last("LIMIT 1"));
        if (existing != null && existing.getStatus() != null && existing.getStatus() == TopicTipReceipt.STATUS_DONE) {
            TopicPost refreshed = getById(postId);
            return refreshed == null || refreshed.getTipTotal() == null ? existing.getAmount() : refreshed.getTipTotal();
        }

        if (existing == null) {
            TopicTipReceipt receipt = new TopicTipReceipt();
            receipt.setPostId(postId);
            receipt.setTipperId(tipperId);
            receipt.setAmount(amount);
            receipt.setRequestId(idempotencyKey);
            receipt.setStatus(TopicTipReceipt.STATUS_PENDING);
            try {
                topicTipReceiptMapper.insert(receipt);
                existing = receipt;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                existing = topicTipReceiptMapper.selectOne(
                        new LambdaQueryWrapper<TopicTipReceipt>()
                                .eq(TopicTipReceipt::getRequestId, idempotencyKey)
                                .last("LIMIT 1"));
                if (existing != null && existing.getStatus() != null
                        && existing.getStatus() == TopicTipReceipt.STATUS_DONE) {
                    TopicPost refreshed = getById(postId);
                    return refreshed == null || refreshed.getTipTotal() == null
                            ? existing.getAmount() : refreshed.getTipTotal();
                }
            }
        }

        int tipAmount = existing != null && existing.getAmount() != null ? existing.getAmount() : amount;

        PointsTransferRequest transfer = new PointsTransferRequest();
        transfer.setFromUserId(tipperId);
        transfer.setToUserId(post.getUserId());
        transfer.setAmount(tipAmount);
        transfer.setReason("TOPIC_TIP");
        transfer.setRefType("TOPIC_TIP");
        transfer.setRefId(postId + ":" + tipperId + ":" + idempotencyKey);
        try {
            unwrap(userFeignClient.tipPoints(transfer));
        } catch (RuntimeException ex) {
            if (existing != null && existing.getId() != null
                    && (existing.getStatus() == null || existing.getStatus() == TopicTipReceipt.STATUS_PENDING)) {
                topicTipReceiptMapper.deleteById(existing.getId());
            }
            throw ex;
        }

        if (existing == null || existing.getStatus() == null
                || existing.getStatus() != TopicTipReceipt.STATUS_DONE) {
            boolean updated = lambdaUpdate()
                    .eq(TopicPost::getId, postId)
                    .setSql("tip_total = IFNULL(tip_total, 0) + " + tipAmount)
                    .update();
            if (!updated) {
                throw new BizException(ResultCode.TOPIC_POST_NOT_FOUND);
            }
            if (existing != null && existing.getId() != null) {
                TopicTipReceipt done = new TopicTipReceipt();
                done.setId(existing.getId());
                done.setStatus(TopicTipReceipt.STATUS_DONE);
                topicTipReceiptMapper.updateById(done);
            }
        }
        TopicPost refreshed = getById(postId);
        return refreshed == null || refreshed.getTipTotal() == null ? tipAmount : refreshed.getTipTotal();
    }

    @Override
    public List<TopicPostVO> listFeed(Integer size) {
        int limit = size == null || size <= 0 ? 20 : Math.min(size, 50);
        List<TopicPost> posts = list(new LambdaQueryWrapper<TopicPost>()
                .last("ORDER BY RAND() LIMIT " + limit));
        List<TopicPostVO> list = posts.stream()
                .map(post -> {
                    TopicPostVO vo = TopicPostVO.from(post);
                    vo.setContent(previewContent(post.getContent()));
                    return vo;
                })
                .collect(Collectors.toList());
        enrichAuthors(list);
        enrichProductCounts(list);
        return list;
    }

    private void notifyLikeQuest(Long userId, Long postId) {
        try {
            userFeignClient.recordLike(userId, postId);
        } catch (Exception e) {
            log.warn("上报每日点赞进度失败. userId={}, postId={}", userId, postId, e);
        }
    }

    private void notifyUnlikeQuest(Long userId, Long postId) {
        try {
            userFeignClient.unrecordLike(userId, postId);
        } catch (Exception e) {
            log.warn("回退每日点赞进度失败. userId={}, postId={}", userId, postId, e);
        }
    }

    private <T> T unwrap(Result<T> result) {
        if (result == null) {
            throw new BizException(ResultCode.INTERNAL_ERROR);
        }
        if (result.getCode() == null || result.getCode() != ResultCode.SUCCESS.getCode()) {
            throw new BizException(
                    result.getCode() == null ? ResultCode.INTERNAL_ERROR.getCode() : result.getCode(),
                    result.getMessage() == null ? ResultCode.INTERNAL_ERROR.getMessage() : result.getMessage());
        }
        return result.getData();
    }

    private String buildTrendingReason(int comments, int upvotes) {
        if (comments > 0) {
            return comments + " 条讨论";
        }
        if (upvotes > 0) {
            return upvotes + " 次点赞";
        }
        return "热门话题";
    }

    private TopicPost requirePost(Long postId) {
        if (postId == null || postId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        TopicPost post = getById(postId);
        if (post == null) {
            throw new BizException(ResultCode.TOPIC_POST_NOT_FOUND);
        }
        return post;
    }

    private List<Long> normalizeProductIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        Set<Long> unique = new LinkedHashSet<>();
        for (Long id : productIds) {
            if (id != null && id > 0) {
                unique.add(id);
            }
        }
        if (unique.size() > MAX_PRODUCTS) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "最多附带5个商品");
        }
        return new ArrayList<>(unique);
    }

    private void validateOwnedProducts(Long userId, List<Long> productIds) {
        for (Long productId : productIds) {
            Product product = productMapper.selectById(productId);
            if (product == null) {
                throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
            }
            if (!userId.equals(product.getSellerId())) {
                throw new BizException(ResultCode.FORBIDDEN.getCode(), "只能附带自己发布的商品");
            }
        }
    }

    private List<ProductListVO> loadAttachedProducts(Long postId) {
        List<TopicPostProduct> links = topicPostProductMapper.selectList(
                new LambdaQueryWrapper<TopicPostProduct>().eq(TopicPostProduct::getPostId, postId));
        if (links.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = links.stream()
                .map(TopicPostProduct::getProductId)
                .collect(Collectors.toList());
        List<Product> products = productMapper.selectBatchIds(productIds);
        Map<Long, Product> productMap = products.stream()
                .filter(p -> p.getId() != null)
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        List<ProductListVO> result = new ArrayList<>();
        for (Long productId : productIds) {
            Product product = productMap.get(productId);
            if (product != null) {
                result.add(ProductListVO.from(product));
            }
        }
        return result;
    }

    private void enrichProductCounts(List<TopicPostVO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> postIds = list.stream()
                .map(TopicPostVO::getPostId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (postIds.isEmpty()) {
            return;
        }
        List<TopicPostProduct> links = topicPostProductMapper.selectList(
                new LambdaQueryWrapper<TopicPostProduct>().in(TopicPostProduct::getPostId, postIds));
        Map<Long, Long> countMap = links.stream()
                .collect(Collectors.groupingBy(TopicPostProduct::getPostId, Collectors.counting()));
        for (TopicPostVO vo : list) {
            vo.setProductCount(countMap.getOrDefault(vo.getPostId(), 0L).intValue());
        }
    }

    private void enrichAuthors(List<TopicPostVO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        String ids = list.stream()
                .map(TopicPostVO::getUserId)
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
            for (TopicPostVO vo : list) {
                UserBriefDTO user = userMap.get(vo.getUserId());
                if (user != null) {
                    vo.setNickname(user.getNickname());
                    vo.setAvatar(user.getAvatar());
                    vo.setBio(user.getBio());
                }
            }
        } catch (Exception e) {
            log.warn("补全话题帖子作者信息失败. ids={}", ids, e);
        }
    }

    private String previewContent(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String text = content
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.length() <= CONTENT_PREVIEW_LEN) {
            return text;
        }
        return text.substring(0, CONTENT_PREVIEW_LEN) + "…";
    }
}
