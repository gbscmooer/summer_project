package com.campus.order.controller;

import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.order.dto.CreateOrderRequest;
import com.campus.order.dto.CreateOrderVO;
import com.campus.order.dto.OrderDetailVO;
import com.campus.order.dto.OrderListVO;
import com.campus.order.dto.OrderReviewRequest;
import com.campus.order.dto.OrderReviewVO;
import com.campus.order.service.OrderReviewService;
import com.campus.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 订单接口（经网关，买家/卖家身份由网关注入的请求头 {@code X-User-Id} 标识）。
 */
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderReviewService orderReviewService;

    /** 下单。body {productId}，X-User-Id = buyerId。 */
    @PostMapping
    public Result<CreateOrderVO> create(
            @RequestHeader("X-User-Id") Long buyerId,
            @Valid @RequestBody CreateOrderRequest request) {
        return Result.success("下单成功", orderService.createOrder(buyerId, request.getProductId()));
    }

    /** 秒杀下单。body {productId}，X-User-Id = buyerId。 */
    @PostMapping("/seckill")
    public Result<java.util.Map<String, String>> seckill(
            @RequestHeader("X-User-Id") Long buyerId,
            @Valid @RequestBody CreateOrderRequest request) {
        String queueId = orderService.seckill(buyerId, request.getProductId());
        return Result.success("排队中，请稍候", java.util.Map.of("queueId", queueId));
    }

    /** 查询秒杀结果。 */
    @GetMapping("/seckill/result/{productId}")
    public Result<com.campus.order.dto.SeckillResultVO> getSeckillResult(
            @RequestHeader("X-User-Id") Long buyerId,
            @PathVariable Long productId) {
        return Result.success(orderService.getSeckillResult(buyerId, productId));
    }

    /** 订单详情（需登录，仅买卖家可见）。 */
    @GetMapping("/{id}")
    public Result<OrderDetailVO> detail(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        return Result.success(orderService.getDetail(userId, id));
    }

    /** 我买到的（分页，status 空=全部，create_time 倒序）。 */
    @GetMapping("/buyer")
    public Result<PageResult<OrderListVO>> buyerOrders(
            @RequestHeader("X-User-Id") Long buyerId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(orderService.buyerOrders(buyerId, status, pageNum, pageSize));
    }

    /** 我卖出的（分页）。 */
    @GetMapping("/seller")
    public Result<PageResult<OrderListVO>> sellerOrders(
            @RequestHeader("X-User-Id") Long sellerId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(orderService.sellerOrders(sellerId, status, pageNum, pageSize));
    }

    /** 商家收入统计（仅商家可用）。 */
    @GetMapping("/seller/stats")
    public Result<com.campus.order.dto.SellerIncomeStatsView> sellerIncomeStats(
            @RequestHeader("X-User-Id") Long sellerId) {
        return Result.success(orderService.getSellerIncomeStats(sellerId));
    }

    /** 商家收入仪表盘（趋势与分布，仅商家可用）。 */
    @GetMapping("/seller/dashboard")
    public Result<com.campus.order.dto.SellerDashboardView> sellerDashboard(
            @RequestHeader("X-User-Id") Long sellerId) {
        return Result.success(orderService.getSellerDashboard(sellerId));
    }

    /** 支付（仅买家本人，0→1）。 */
    @PostMapping("/{id}/pay")
    public Result<Void> pay(
            @RequestHeader("X-User-Id") Long buyerId,
            @PathVariable Long id) {
        orderService.pay(buyerId, id);
        return Result.success("支付成功", null);
    }

    /** 确认收货（仅买家本人，1→2）。 */
    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(
            @RequestHeader("X-User-Id") Long buyerId,
            @PathVariable Long id) {
        orderService.confirm(buyerId, id);
        return Result.success("确认收货成功", null);
    }

    /** 取消（仅买家本人，0→3，回滚库存）。 */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(
            @RequestHeader("X-User-Id") Long buyerId,
            @PathVariable Long id) {
        orderService.cancel(buyerId, id);
        return Result.success("已取消", null);
    }

    /** 提交交易评价（仅买家，订单已完成且 7 天内，一单一评）。 */
    @PostMapping("/{id}/review")
    public Result<Map<String, Long>> submitReview(
            @RequestHeader("X-User-Id") Long buyerId,
            @PathVariable Long id,
            @Valid @RequestBody OrderReviewRequest request) {
        Long reviewId = orderReviewService.submitReview(buyerId, id, request);
        return Result.success("评价成功", Map.of("reviewId", reviewId));
    }

    /** 查询订单评价（仅买卖家可见；未评价时 data 为 null）。 */
    @GetMapping("/{id}/review")
    public Result<OrderReviewVO> getReview(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        return Result.success(orderReviewService.getByOrder(userId, id));
    }

    /** 卖家收到的评价列表（公开分页）。 */
    @GetMapping("/reviews/seller/{sellerId}")
    public Result<PageResult<OrderReviewVO>> listSellerReviews(
            @PathVariable Long sellerId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(orderReviewService.listBySeller(sellerId, pageNum, pageSize));
    }
}
