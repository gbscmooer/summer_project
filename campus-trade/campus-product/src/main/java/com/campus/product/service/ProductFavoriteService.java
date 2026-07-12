package com.campus.product.service;

import com.campus.common.result.PageResult;
import com.campus.product.dto.FavoriteProductVO;
import com.campus.product.dto.FavoriteStatusVO;

public interface ProductFavoriteService {

    FavoriteStatusVO toggle(Long userId, Long productId);

    FavoriteStatusVO status(Long userId, Long productId);

    PageResult<FavoriteProductVO> listMine(Long userId, Integer pageNum, Integer pageSize);
}
