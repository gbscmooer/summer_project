package com.campus.product.service;

import com.campus.common.result.PageResult;
import com.campus.product.dto.ProductCommentRequest;
import com.campus.product.dto.ProductCommentVO;

public interface ProductCommentService {

    PageResult<ProductCommentVO> listByProduct(Long productId, Integer pageNum, Integer pageSize);

    Long addComment(Long userId, Long productId, ProductCommentRequest request);
}
