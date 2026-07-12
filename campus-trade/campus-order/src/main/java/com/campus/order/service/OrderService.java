package com.campus.order.service;

import com.campus.common.result.PageResult;
import com.campus.order.dto.CreateOrderVO;
import com.campus.order.dto.OrderDetailVO;
import com.campus.order.dto.OrderListVO;
import com.campus.order.dto.SellerDashboardView;
import com.campus.order.dto.SellerIncomeStatsView;

/**
 * 订单业务接口：下单、查询、状态流转。
 */
public interface OrderService {

    /** 下单：校验商品状态/非本人 → 扣库存 → 落库。 */
    CreateOrderVO createOrder(Long buyerId, Long productId);

    /** 订单详情：权限校验（仅买卖家可见），批量补买卖家昵称。 */
    OrderDetailVO getDetail(Long userId, Long orderId);

    /** 我买到的：按 buyerId 分页，对方昵称取卖家。 */
    PageResult<OrderListVO> buyerOrders(Long buyerId, Integer status, Integer pageNum, Integer pageSize);

    /** 我卖出的：按 sellerId 分页，对方昵称取买家。 */
    PageResult<OrderListVO> sellerOrders(Long sellerId, Integer status, Integer pageNum, Integer pageSize);

    /** 支付：仅买家本人，状态 0→1。 */
    void pay(Long buyerId, Long orderId);

    /** 确认收货：仅买家本人，状态 1→2（商品已在扣库存时置已售，无需通知商品服务）。 */
    void confirm(Long buyerId, Long orderId);

    /** 取消：仅买家本人，状态 0→3，并回滚商品库存。 */
    void cancel(Long buyerId, Long orderId);

    /** 秒杀预扣与排队。 */
    String seckill(Long buyerId, Long productId);

    /** 异步消费秒杀消息并落库，带补偿事务。 */
    String createSeckillOrder(String requestId, Long buyerId, Long productId);

    /** 查询秒杀排队与下单结果。 */
    com.campus.order.dto.SeckillResultVO getSeckillResult(Long buyerId, Long productId);

    /** 商家收入统计（仅商家可用） */
    SellerIncomeStatsView getSellerIncomeStats(Long sellerId);

    /** 商家收入仪表盘（趋势图、订单分布，仅商家可用） */
    SellerDashboardView getSellerDashboard(Long sellerId);
}
