package com.campus.product.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.campus.common.exception.BizException;
import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import com.campus.common.util.PageParamUtil;
import com.campus.product.dto.ProductCommentRequest;
import com.campus.product.dto.ProductCommentVO;
import com.campus.product.entity.Product;
import com.campus.product.entity.ProductComment;
import com.campus.product.feign.UserFeignClient;
import com.campus.product.feign.dto.UserBriefDTO;
import com.campus.product.mapper.ProductCommentMapper;
import com.campus.product.mapper.ProductMapper;
import com.campus.product.service.ProductCommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCommentServiceImpl extends ServiceImpl<ProductCommentMapper, ProductComment>
        implements ProductCommentService {

    private final ProductMapper productMapper;
    private final UserFeignClient userFeignClient;

    @Override
    public PageResult<ProductCommentVO> listByProduct(Long productId, Integer pageNum, Integer pageSize) {
        requireProduct(productId);
        int pageNo = PageParamUtil.normalizePageNum(pageNum);
        int size = PageParamUtil.normalizePageSize(pageSize);
        Page<ProductComment> page = new Page<>(pageNo, size);
        lambdaQuery()
                .eq(ProductComment::getProductId, productId)
                .orderByDesc(ProductComment::getCreateTime)
                .page(page);

        List<ProductCommentVO> list = page.getRecords().stream()
                .map(ProductCommentVO::from)
                .collect(Collectors.toList());
        enrichUserInfo(list);
        return PageResult.of(page.getTotal(), pageNo, size, list);
    }

    @Override
    public Long addComment(Long userId, Long productId, ProductCommentRequest request) {
        if (request == null || !StringUtils.hasText(request.getContent())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "请填写留言内容");
        }
        requireProduct(productId);
        ProductComment comment = new ProductComment();
        comment.setProductId(productId);
        comment.setUserId(userId);
        comment.setContent(request.getContent().trim());
        save(comment);
        return comment.getId();
    }

    private Product requireProduct(Long productId) {
        if (productId == null || productId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }
        return product;
    }

    private void enrichUserInfo(List<ProductCommentVO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        String ids = list.stream()
                .map(ProductCommentVO::getUserId)
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
            for (ProductCommentVO vo : list) {
                UserBriefDTO user = userMap.get(vo.getUserId());
                if (user == null) {
                    continue;
                }
                vo.setNickname(user.getNickname());
            }
        } catch (Exception e) {
            log.warn("补全商品留言用户信息失败，降级返回基础留言. ids={}", ids, e);
        }
    }
}
