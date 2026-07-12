package com.campus.order.service;

import com.campus.common.result.PageResult;
import com.campus.order.dto.OrderReviewRequest;
import com.campus.order.dto.OrderReviewVO;

public interface OrderReviewService {

    /** 买家提交评价：订单已完成、7 天内、一单一评。 */
    Long submitReview(Long buyerId, Long orderId, OrderReviewRequest request);

    /** 查询订单评价；不存在返回 null（仅买卖家可见）。 */
    OrderReviewVO getByOrder(Long userId, Long orderId);

    /** 卖家收到的评价列表（公开分页）。 */
    PageResult<OrderReviewVO> listBySeller(Long sellerId, Integer pageNum, Integer pageSize);
}
