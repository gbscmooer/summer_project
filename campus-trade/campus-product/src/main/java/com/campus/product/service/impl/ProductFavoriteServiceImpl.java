package com.campus.product.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.campus.common.exception.BizException;
import com.campus.common.result.PageResult;
import com.campus.common.result.ResultCode;
import com.campus.common.util.PageParamUtil;
import com.campus.product.dto.FavoriteProductVO;
import com.campus.product.dto.FavoriteStatusVO;
import com.campus.product.entity.Product;
import com.campus.product.entity.ProductFavorite;
import com.campus.product.mapper.ProductFavoriteMapper;
import com.campus.product.mapper.ProductMapper;
import com.campus.product.service.ProductFavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductFavoriteServiceImpl extends ServiceImpl<ProductFavoriteMapper, ProductFavorite>
        implements ProductFavoriteService {

    private final ProductMapper productMapper;

    @Override
    @Transactional
    public FavoriteStatusVO toggle(Long userId, Long productId) {
        requireUserId(userId);
        requireProductAvailable(productId);

        // 先删后插：避免先查再写的竞态；唯一约束兜底并发双插
        boolean removed = lambdaUpdate()
                .eq(ProductFavorite::getUserId, userId)
                .eq(ProductFavorite::getProductId, productId)
                .remove();
        if (removed) {
            return new FavoriteStatusVO(false, countByProduct(productId));
        }

        ProductFavorite favorite = new ProductFavorite();
        favorite.setUserId(userId);
        favorite.setProductId(productId);
        try {
            save(favorite);
        } catch (DuplicateKeyException e) {
            // 并发下另一请求已插入：视为已收藏
        }
        return new FavoriteStatusVO(true, countByProduct(productId));
    }

    @Override
    public FavoriteStatusVO status(Long userId, Long productId) {
        requireUserId(userId);
        requireProduct(productId);
        boolean favorited = lambdaQuery()
                .eq(ProductFavorite::getUserId, userId)
                .eq(ProductFavorite::getProductId, productId)
                .exists();
        return new FavoriteStatusVO(favorited, countByProduct(productId));
    }

    @Override
    public PageResult<FavoriteProductVO> listMine(Long userId, Integer pageNum, Integer pageSize) {
        requireUserId(userId);
        int pageNo = PageParamUtil.normalizePageNum(pageNum);
        int size = PageParamUtil.normalizePageSize(pageSize);
        Page<ProductFavorite> page = new Page<>(pageNo, size);
        // 仅展示在售/已售（未删除）；下架 (status=0) 过滤掉
        lambdaQuery()
                .eq(ProductFavorite::getUserId, userId)
                .inSql(ProductFavorite::getProductId, "SELECT id FROM t_product WHERE status IN (1, 2)")
                .orderByDesc(ProductFavorite::getCreateTime)
                .page(page);

        List<ProductFavorite> records = page.getRecords();
        if (records == null || records.isEmpty()) {
            return PageResult.of(page.getTotal(), pageNo, size, Collections.emptyList());
        }

        List<Long> productIds = records.stream()
                .map(ProductFavorite::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Product> productMap = productIds.isEmpty()
                ? Collections.emptyMap()
                : productMapper.selectBatchIds(productIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));

        List<FavoriteProductVO> list = records.stream()
                .map(fav -> {
                    Product product = productMap.get(fav.getProductId());
                    if (product == null) {
                        return null;
                    }
                    return FavoriteProductVO.from(product, fav.getCreateTime());
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return PageResult.of(page.getTotal(), pageNo, size, list);
    }

    private long countByProduct(Long productId) {
        return lambdaQuery().eq(ProductFavorite::getProductId, productId).count();
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
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

    /** 收藏仅允许在售/已售商品；下架不可再收藏。 */
    private Product requireProductAvailable(Long productId) {
        Product product = requireProduct(productId);
        Integer status = product.getStatus();
        if (status == null || (status != 1 && status != 2)) {
            throw new BizException(ResultCode.PRODUCT_OFF_SHELF);
        }
        return product;
    }
}
