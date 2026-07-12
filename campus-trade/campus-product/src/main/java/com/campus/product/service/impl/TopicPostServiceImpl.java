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
import com.campus.product.entity.Product;
import com.campus.product.entity.TopicPost;
import com.campus.product.entity.TopicPostProduct;
import com.campus.product.feign.UserFeignClient;
import com.campus.product.feign.dto.UserBriefDTO;
import com.campus.product.mapper.ProductMapper;
import com.campus.product.mapper.TopicPostMapper;
import com.campus.product.mapper.TopicPostProductMapper;
import com.campus.product.service.TopicPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicPostServiceImpl extends ServiceImpl<TopicPostMapper, TopicPost> implements TopicPostService {

    private static final int CONTENT_PREVIEW_LEN = 160;
    private static final int MAX_PRODUCTS = 5;

    private final TopicPostProductMapper topicPostProductMapper;
    private final ProductMapper productMapper;
    private final UserFeignClient userFeignClient;

    @Override
    public PageResult<TopicPostVO> listPosts(Integer pageNum, Integer pageSize) {
        int pageNo = PageParamUtil.normalizePageNum(pageNum);
        int size = PageParamUtil.normalizePageSize(pageSize);
        Page<TopicPost> page = new Page<>(pageNo, size);
        lambdaQuery()
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
    public TopicPostVO getDetail(Long postId) {
        TopicPost post = requirePost(postId);
        TopicPostVO vo = TopicPostVO.from(post);
        vo.setProducts(loadAttachedProducts(postId));
        vo.setProductCount(vo.getProducts() == null ? 0 : vo.getProducts().size());
        enrichAuthors(Collections.singletonList(vo));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPost(Long userId, TopicPostRequest request) {
        if (request == null || !StringUtils.hasText(request.getTitle()) || !StringUtils.hasText(request.getContent())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "请填写标题和正文");
        }
        List<Long> productIds = normalizeProductIds(request.getProductIds());
        validateOwnedProducts(userId, productIds);

        TopicPost post = new TopicPost();
        post.setUserId(userId);
        post.setTitle(request.getTitle().trim());
        post.setContent(request.getContent().trim());
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
        removeById(postId);
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
        String text = content.trim();
        if (text.length() <= CONTENT_PREVIEW_LEN) {
            return text;
        }
        return text.substring(0, CONTENT_PREVIEW_LEN) + "…";
    }
}
