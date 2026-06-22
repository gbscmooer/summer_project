package com.campus.product.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BizException;
import com.campus.common.result.PageResult;
import com.campus.common.result.ResultCode;
import com.campus.product.dto.ProductDetailVO;
import com.campus.product.dto.ProductListVO;
import com.campus.product.dto.ProductRequest;
import com.campus.product.entity.Product;
import com.campus.product.mapper.ProductMapper;
import com.campus.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    @Override
    public Long publish(Long sellerId, ProductRequest request) {
        Product product = new Product();
        product.setSellerId(sellerId);
        product.setTitle(request.getTitle());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setImages(request.getImages());
        product.setCategory(request.getCategory());
        product.setStock(request.getStock());
        product.setStatus(1);
        product.setViewCount(0);
        save(product);
        return product.getId();
    }

    @Override
    public void update(Long sellerId, Long productId, ProductRequest request) {
        Product product = getAndCheckOwner(sellerId, productId);
        product.setTitle(request.getTitle());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setImages(request.getImages());
        product.setCategory(request.getCategory());
        if (request.getStock() != null) product.setStock(request.getStock());
        updateById(product);
    }

    @Override
    public void remove(Long sellerId, Long productId) {
        Product product = getAndCheckOwner(sellerId, productId);
        product.setStatus(0);
        updateById(product);
    }

    @Override
    public ProductDetailVO getDetail(Long productId) {
        Product product = getById(productId);
        if (product == null) throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        baseMapper.incrementViewCount(productId);
        product.setViewCount(product.getViewCount() + 1);
        return ProductDetailVO.from(product);
    }

    @Override
    public PageResult<ProductListVO> listProducts(String category, Integer pageNum, Integer pageSize) {
        Page<Product> page = new Page<>(pageNum, pageSize);
        lambdaQuery()
                .eq(Product::getStatus, 1)
                .eq(StringUtils.hasText(category), Product::getCategory, category)
                .orderByDesc(Product::getCreateTime)
                .page(page);
        List<ProductListVO> list = page.getRecords().stream()
                .map(ProductListVO::from).collect(Collectors.toList());
        return PageResult.of(page.getTotal(), pageNum, pageSize, list);
    }

    @Override
    public PageResult<ProductListVO> myProducts(Long sellerId, Integer pageNum, Integer pageSize) {
        Page<Product> page = new Page<>(pageNum, pageSize);
        lambdaQuery()
                .eq(Product::getSellerId, sellerId)
                .orderByDesc(Product::getCreateTime)
                .page(page);
        List<ProductListVO> list = page.getRecords().stream()
                .map(ProductListVO::from).collect(Collectors.toList());
        return PageResult.of(page.getTotal(), pageNum, pageSize, list);
    }

    @Override
    public Product innerGetProduct(Long productId) {
        Product product = getById(productId);
        if (product == null) throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        return product;
    }

    @Override
    public boolean deductStock(Long productId) {
        Product product = getById(productId);
        if (product == null) throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        if (product.getStatus() != 1) throw new BizException(ResultCode.PRODUCT_OFF_SHELF);
        if (product.getStock() <= 0) throw new BizException(ResultCode.PRODUCT_STOCK_INSUFFICIENT);
        int updated = baseMapper.deductStock(productId);
        if (updated == 0) throw new BizException(ResultCode.PRODUCT_STOCK_INSUFFICIENT);
        // 库存归零时自动标记已售
        if (product.getStock() - 1 == 0) {
            lambdaUpdate().eq(Product::getId, productId).set(Product::getStatus, 2).update();
        }
        return true;
    }

    @Override
    public void restoreStock(Long productId) {
        baseMapper.restoreStock(productId);
        lambdaUpdate().eq(Product::getId, productId).set(Product::getStatus, 1).update();
    }

    private Product getAndCheckOwner(Long sellerId, Long productId) {
        Product product = getById(productId);
        if (product == null) throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        if (!product.getSellerId().equals(sellerId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return product;
    }
}
