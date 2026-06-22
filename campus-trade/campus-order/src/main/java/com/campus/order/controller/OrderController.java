package com.campus.order.controller;

import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.order.dto.CreateOrderRequest;
import com.campus.order.dto.CreateOrderVO;
import com.campus.order.dto.OrderDetailVO;
import com.campus.order.dto.OrderListVO;
import com.campus.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 订单接口（经网关，买家/卖家身份由网关注入的请求头 {@code X-User-Id} 标识）。
 */
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** 下单。body {productId}，X-User-Id = buyerId。 */
    @PostMapping
    public Result<CreateOrderVO> create(
            @RequestHeader("X-User-Id") Long buyerId,
            @Valid @RequestBody CreateOrderRequest request) {
        return Result.success("下单成功", orderService.createOrder(buyerId, request.getProductId()));
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

    /** 支付（仅买家本人，0→1）。 */
    @PutMapping("/{id}/pay")
    public Result<Void> pay(
            @RequestHeader("X-User-Id") Long buyerId,
            @PathVariable Long id) {
        orderService.pay(buyerId, id);
        return Result.success("支付成功", null);
    }

    /** 确认收货（仅买家本人，1→2）。 */
    @PutMapping("/{id}/confirm")
    public Result<Void> confirm(
            @RequestHeader("X-User-Id") Long buyerId,
            @PathVariable Long id) {
        orderService.confirm(buyerId, id);
        return Result.success("确认收货成功", null);
    }

    /** 取消（仅买家本人，0→3，回滚库存）。 */
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(
            @RequestHeader("X-User-Id") Long buyerId,
            @PathVariable Long id) {
        orderService.cancel(buyerId, id);
        return Result.success("已取消", null);
    }
}
