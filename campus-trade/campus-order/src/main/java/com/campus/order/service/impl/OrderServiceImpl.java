package com.campus.order.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BizException;
import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import com.campus.order.dto.CreateOrderVO;
import com.campus.order.dto.OrderDetailVO;
import com.campus.order.dto.OrderListVO;
import com.campus.order.entity.Order;
import com.campus.order.feign.ProductFeignClient;
import com.campus.order.feign.UserFeignClient;
import com.campus.order.feign.dto.ProductDTO;
import com.campus.order.feign.dto.UserBriefDTO;
import com.campus.order.mapper.OrderMapper;
import com.campus.order.service.OrderService;
import com.campus.order.util.OrderNoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 订单业务实现。
 *
 * <p>关键约定：商品/用户服务返回 HTTP 200 但用 {@code Result.code} 区分业务成败，
 * Feign 默认只在 4xx/5xx 抛异常，因此每次 Feign 调用后都必须经
 * {@link #unwrap(Result)} 检查 code，把下游业务错误码（2001/2002/2003）透传出来。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private final ProductFeignClient productFeign;
    private final UserFeignClient userFeign;

    // ==================== 状态常量 ====================
    private static final int STATUS_UNPAID = 0;
    private static final int STATUS_PAID = 1;
    private static final int STATUS_DONE = 2;
    private static final int STATUS_CANCELLED = 3;

    @Override
    public CreateOrderVO createOrder(Long buyerId, Long productId) {
        // 1. 查商品（透传 2001 商品不存在）
        ProductDTO product = unwrap(productFeign.getProduct(productId));
        if (product == null) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }
        // 2. 商品必须在售（status==1）
        if (product.getStatus() == null || product.getStatus() != 1) {
            throw new BizException(ResultCode.PRODUCT_OFF_SHELF);
        }
        // 3. 不能购买自己发布的商品
        if (buyerId.equals(product.getSellerId())) {
            throw new BizException(ResultCode.ORDER_CANNOT_BUY_OWN);
        }
        // 4. 扣库存（库存不足透传 2003；扣到 0 商品服务会自动置已售）
        unwrap(productFeign.deductStock(productId));

        // 5. 生成订单号 + 6. 落库（冗余 title/price/seller，status=待付款）
        Order order = new Order();
        order.setOrderNo(OrderNoGenerator.generate());
        order.setProductId(productId);
        order.setProductTitle(product.getTitle());
        order.setPrice(product.getPrice());
        order.setBuyerId(buyerId);
        order.setSellerId(product.getSellerId());
        order.setStatus(STATUS_UNPAID);
        save(order);

        // TODO P6: 发送下单通知到 RabbitMQ
        return CreateOrderVO.from(order);
    }

    @Override
    public OrderDetailVO getDetail(Long userId, Long orderId) {
        Order order = getExistingOrder(orderId);
        // 权限：仅买家或卖家本人可查看
        if (!userId.equals(order.getBuyerId()) && !userId.equals(order.getSellerId())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        OrderDetailVO vo = OrderDetailVO.from(order);
        vo.setStatusText(statusText(order.getStatus()));

        // 一次性批量补买卖家昵称
        Map<Long, String> nicknames = resolveNicknames(List.of(order.getBuyerId(), order.getSellerId()));
        vo.setBuyerNickname(nicknames.get(order.getBuyerId()));
        vo.setSellerNickname(nicknames.get(order.getSellerId()));
        return vo;
    }

    @Override
    public PageResult<OrderListVO> buyerOrders(Long buyerId, Integer status, Integer pageNum, Integer pageSize) {
        Page<Order> page = new Page<>(pageNum, pageSize);
        lambdaQuery()
                .eq(Order::getBuyerId, buyerId)
                .eq(status != null, Order::getStatus, status)
                .orderByDesc(Order::getCreateTime)
                .page(page);
        // 买家视角：对方=卖家
        return toListResult(page, pageNum, pageSize, Order::getSellerId);
    }

    @Override
    public PageResult<OrderListVO> sellerOrders(Long sellerId, Integer status, Integer pageNum, Integer pageSize) {
        Page<Order> page = new Page<>(pageNum, pageSize);
        lambdaQuery()
                .eq(Order::getSellerId, sellerId)
                .eq(status != null, Order::getStatus, status)
                .orderByDesc(Order::getCreateTime)
                .page(page);
        // 卖家视角：对方=买家
        return toListResult(page, pageNum, pageSize, Order::getBuyerId);
    }

    @Override
    public void pay(Long buyerId, Long orderId) {
        Order order = getOwnedOrder(buyerId, orderId);
        if (order.getStatus() != STATUS_UNPAID) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID);
        }
        order.setStatus(STATUS_PAID);
        updateById(order);
    }

    @Override
    public void confirm(Long buyerId, Long orderId) {
        Order order = getOwnedOrder(buyerId, orderId);
        if (order.getStatus() != STATUS_PAID) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID);
        }
        // 商品在扣库存时已自动置已售，确认收货只流转订单状态
        order.setStatus(STATUS_DONE);
        updateById(order);
    }

    @Override
    public void cancel(Long buyerId, Long orderId) {
        Order order = getOwnedOrder(buyerId, orderId);
        if (order.getStatus() != STATUS_UNPAID) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID);
        }
        order.setStatus(STATUS_CANCELLED);
        updateById(order);
        // 回滚库存（库存+1 且把商品状态恢复为在售），检查返回 code
        unwrap(productFeign.restoreStock(order.getProductId()));
    }

    // ==================== 私有辅助 ====================

    /**
     * 透传 Feign 业务结果：HTTP 200 但 {@code Result.code != 200} 时把下游业务码抛出。
     */
    private <T> T unwrap(Result<T> result) {
        if (result == null) {
            throw new BizException(ResultCode.INTERNAL_ERROR);
        }
        if (result.getCode() == null || result.getCode() != ResultCode.SUCCESS.getCode()) {
            throw new BizException(result.getCode() == null ? ResultCode.INTERNAL_ERROR.getCode() : result.getCode(),
                    result.getMessage());
        }
        return result.getData();
    }

    /** 查订单，不存在抛 3001。 */
    private Order getExistingOrder(Long orderId) {
        Order order = getById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    /** 查订单并校验操作者是买家本人（状态流转三接口共用）：不存在 3001、非本人 403。 */
    private Order getOwnedOrder(Long buyerId, Long orderId) {
        Order order = getExistingOrder(orderId);
        if (!buyerId.equals(order.getBuyerId())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return order;
    }

    /** 状态码 → 文案。 */
    private String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case STATUS_UNPAID -> "待付款";
            case STATUS_PAID -> "已付款";
            case STATUS_DONE -> "已完成";
            case STATUS_CANCELLED -> "已取消";
            default -> "未知";
        };
    }

    /**
     * 组装分页列表：收集本页所有"对方" userId，一次 batch 补昵称，再逐条填充。
     *
     * @param counterpartIdGetter 从订单取"对方" userId（买家视角取卖家，卖家视角取买家）
     */
    private PageResult<OrderListVO> toListResult(Page<Order> page, Integer pageNum, Integer pageSize,
                                                 Function<Order, Long> counterpartIdGetter) {
        List<Order> records = page.getRecords();
        // 收集本页所有相关对方 id，一次查全部（禁止逐条调 Feign）
        Set<Long> counterpartIds = records.stream()
                .map(counterpartIdGetter)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, String> nicknames = resolveNicknames(counterpartIds);

        List<OrderListVO> list = records.stream().map(order -> {
            OrderListVO vo = OrderListVO.from(order);
            vo.setStatusText(statusText(order.getStatus()));
            vo.setCounterpartNickname(nicknames.get(counterpartIdGetter.apply(order)));
            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(page.getTotal(), pageNum, pageSize, list);
    }

    /**
     * 批量解析 userId → nickname。空集合直接返回空 Map，避免无谓的 Feign 调用。
     */
    private Map<Long, String> resolveNicknames(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String ids = userIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        List<UserBriefDTO> users = unwrap(userFeign.batchGetUsers(ids));
        if (users == null) {
            return Collections.emptyMap();
        }
        return users.stream()
                .filter(u -> u.getUserId() != null)
                .collect(Collectors.toMap(UserBriefDTO::getUserId, UserBriefDTO::getNickname, (a, b) -> a));
    }
}
