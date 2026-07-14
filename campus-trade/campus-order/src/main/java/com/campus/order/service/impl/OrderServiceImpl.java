package com.campus.order.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.campus.common.constant.UserRole;
import com.campus.common.exception.BizException;
import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import com.campus.common.util.PageParamUtil;
import com.campus.order.dto.CreateOrderVO;
import com.campus.order.dto.OrderDetailVO;
import com.campus.order.dto.OrderListVO;
import com.campus.order.dto.SellerIncomeStatsView;
import com.campus.order.entity.Order;
import com.campus.order.feign.ProductFeignClient;
import com.campus.order.feign.UserFeignClient;
import com.campus.order.feign.dto.ProductDTO;
import com.campus.order.feign.dto.UserBriefDTO;
import com.campus.order.mapper.OrderMapper;
import com.campus.order.service.OrderService;
import com.campus.order.service.StockCompensationService;
import com.campus.order.util.OrderNoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campus.order.config.RabbitMQConfig;
import com.campus.order.dto.OrderNotifyMessage;
import com.campus.order.dto.SeckillMessage;
import com.campus.order.dto.SeckillResultVO;

import java.time.Duration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
    private final RabbitTemplate rabbitTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final StockCompensationService stockCompensationService;

    // ==================== 状态常量 ====================
    private static final int STATUS_UNPAID = 0;
    private static final int STATUS_PAID = 1;
    private static final int STATUS_DONE = 2;
    private static final int STATUS_CANCELLED = 3;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateOrderVO createOrder(Long buyerId, Long productId) {
        // 1. 查商品（透传 2001 商品不存在）
        ProductDTO product = unwrap(productFeign.getProduct(productId));
        if (product == null) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }
        if (product.getSaleType() != null && product.getSaleType() == 1) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "秒杀商品请走秒杀下单");
        }
        // 2. 商品必须在售（status==1）
        if (product.getStatus() == null || product.getStatus() != 1) {
            throw new BizException(ResultCode.PRODUCT_OFF_SHELF);
        }
        // 3. 不能购买自己发布的商品
        if (buyerId.equals(product.getSellerId())) {
            throw new BizException(ResultCode.ORDER_CANNOT_BUY_OWN);
        }
        enforcePurchaseLimit(buyerId, productId, product);

        // 生成唯一订单号，用于幂等库存回滚
        String orderNo = OrderNoGenerator.generate();
        stockCompensationService.register(productId, orderNo);
        stockCompensationService.lockForOrderTransaction(orderNo);

        // 4. 扣库存（库存不足透传 2003；扣到 0 商品服务会自动置已售）
        try {
            unwrap(productFeign.deductStock(productId, orderNo, false));
        } catch (BizException e) {
            if (isDefinitiveNoDeduction(e)) {
                stockCompensationService.completeAfterCompletion(orderNo);
            }
            throw e;
        }

        try {
            // 5. 落库（冗余 title/price/seller，status=待付款）
            Order order = new Order();
            order.setOrderNo(orderNo);
            order.setProductId(productId);
            order.setProductTitle(product.getTitle());
            order.setPrice(product.getPrice());
            order.setBuyerId(buyerId);
            order.setSellerId(product.getSellerId());
            order.setStatus(STATUS_UNPAID);
            save(order);

            // 6. 发送下单通知到 RabbitMQ
            OrderNotifyMessage notifyMessage = new OrderNotifyMessage(
                    order.getOrderNo(),
                    order.getProductId(),
                    order.getProductTitle(),
                    order.getPrice(),
                    order.getBuyerId(),
                    order.getSellerId()
            );
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.ORDER_EXCHANGE,
                    RabbitMQConfig.ORDER_NOTIFY_ROUTING_KEY,
                    notifyMessage);

            stockCompensationService.completeAfterCommit(orderNo);
            return CreateOrderVO.from(order);
        } catch (Exception e) {
            log.error("常规订单落库或消息发送异常，启动远程库存补偿回滚. productId={}, orderNo={}", productId, orderNo, e);
            try {
                unwrap(productFeign.restoreStock(productId, orderNo));
                stockCompensationService.completeAfterCompletion(orderNo);
            } catch (Exception ex) {
                log.error("CRITICAL: 常规订单补偿回滚库存失败. productId={}, orderNo={}", productId, orderNo, ex);
            }
            throw e;
        }
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
        int pageNo = PageParamUtil.normalizePageNum(pageNum);
        int size = PageParamUtil.normalizePageSize(pageSize);
        Page<Order> page = new Page<>(pageNo, size);
        lambdaQuery()
                .eq(Order::getBuyerId, buyerId)
                .eq(status != null, Order::getStatus, status)
                .orderByDesc(Order::getCreateTime)
                .page(page);
        // 买家视角：对方=卖家
        return toListResult(page, pageNo, size, Order::getSellerId);
    }

    @Override
    public PageResult<OrderListVO> sellerOrders(Long sellerId, Integer status, Integer pageNum, Integer pageSize) {
        int pageNo = PageParamUtil.normalizePageNum(pageNum);
        int size = PageParamUtil.normalizePageSize(pageSize);
        Page<Order> page = new Page<>(pageNo, size);
        lambdaQuery()
                .eq(Order::getSellerId, sellerId)
                .eq(status != null, Order::getStatus, status)
                .orderByDesc(Order::getCreateTime)
                .page(page);
        // 卖家视角：对方=买家
        return toListResult(page, pageNo, size, Order::getBuyerId);
    }

    @Override
    public void pay(Long buyerId, Long orderId) {
        Order order = getOwnedOrder(buyerId, orderId);
        int points = toPoints(order.getPrice());
        if (points < 0) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "订单积分无效");
        }

        // 0 积分订单：无需划转，仅推进状态（新手礼包等）
        boolean needTransfer = points > 0;
        com.campus.order.feign.dto.PointsTransferRequest transfer = null;
        if (needTransfer) {
            transfer = new com.campus.order.feign.dto.PointsTransferRequest();
            transfer.setFromUserId(order.getBuyerId());
            transfer.setToUserId(order.getSellerId());
            transfer.setAmount(points);
            transfer.setReason("ORDER_PAY");
            transfer.setRefType("ORDER");
            transfer.setRefId(String.valueOf(order.getId()));
        }

        // 已支付：幂等补齐积分划转后直接返回（崩溃自愈）
        if (order.getStatus() != null && order.getStatus() == STATUS_PAID) {
            if (needTransfer) {
                transferPointsOrRollback(orderId, transfer);
            }
            return;
        }
        if (order.getStatus() == null || order.getStatus() != STATUS_UNPAID) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID);
        }

        // 先短事务 CAS 占住 PAID，再在事务外 Feign，避免长时间占用订单库连接
        boolean updated = lambdaUpdate()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, STATUS_UNPAID)
                .set(Order::getStatus, STATUS_PAID)
                .update();
        if (!updated) {
            Order latest = getById(orderId);
            if (latest != null && latest.getStatus() != null && latest.getStatus() == STATUS_PAID
                    && buyerId.equals(latest.getBuyerId())) {
                if (needTransfer) {
                    transferPointsOrRollback(orderId, transfer);
                }
                return;
            }
            throw new BizException(ResultCode.ORDER_STATUS_INVALID);
        }

        if (!needTransfer) {
            return;
        }

        transferPointsOrRollback(orderId, transfer);
    }

    /**
     * 调用积分划转；若积分侧确认未划转（明确业务失败），将订单从 PAID 回滚为 UNPAID，
     * 避免买家确认收货却未扣款、卖家丢货/丢积分。超时等未知结果保持 PAID 待幂等重试。
     */
    private void transferPointsOrRollback(Long orderId,
                                          com.campus.order.feign.dto.PointsTransferRequest transfer) {
        try {
            unwrap(userFeign.transferPoints(transfer));
        } catch (BizException ex) {
            if (isDefinitivePayFailure(ex)) {
                // 明确失败（积分不足/封禁/用户不存在等）：回滚订单状态
                lambdaUpdate()
                        .eq(Order::getId, orderId)
                        .eq(Order::getStatus, STATUS_PAID)
                        .set(Order::getStatus, STATUS_UNPAID)
                        .update();
            } else {
                // 业务码未知：保持 PAID，依赖积分划转幂等重试自愈
                log.error("支付积分划转业务失败且非明确可回滚，订单保持已支付. orderId={}, code={}",
                        orderId, ex.getCode(), ex);
            }
            throw ex;
        } catch (RuntimeException ex) {
            // 超时/网络等：积分可能已扣成功，禁止回滚为未支付，避免「扣积分 + 取消订单还库存」
            log.error("支付积分划转结果未知，订单保持已支付待重试. orderId={}", orderId, ex);
            throw ex;
        }
    }

    /** 明确可回滚的支付失败：积分侧确认未划转（事务已回滚，无流水）。 */
    static boolean isDefinitivePayFailure(BizException exception) {
        int code = exception.getCode();
        return code == ResultCode.POINTS_INSUFFICIENT.getCode()
                || code == ResultCode.BAD_REQUEST.getCode()
                || code == ResultCode.USER_NOT_FOUND.getCode()
                || code == ResultCode.NOT_FOUND.getCode()
                || code == ResultCode.USER_BANNED.getCode();
    }

    /** 将订单成交积分（DECIMAL）转为整数积分。 */
    private static int toPoints(BigDecimal price) {
        if (price == null) {
            return 0;
        }
        return price.setScale(0, java.math.RoundingMode.HALF_UP).intValue();
    }

    @Override
    public void confirm(Long buyerId, Long orderId) {
        Order order = getOwnedOrder(buyerId, orderId);
        LocalDateTime completedAt = LocalDateTime.now();
        boolean updated = lambdaUpdate()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, STATUS_PAID)
                .set(Order::getStatus, STATUS_DONE)
                .set(Order::getCompletedAt, completedAt)
                .update();
        if (!updated) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long buyerId, Long orderId) {
        Order order = getOwnedOrder(buyerId, orderId);
        if (!tryCancelUnpaidAndRestore(order)) {
            Order currentOrder = getById(orderId);
            if (currentOrder != null && currentOrder.getStatus() == STATUS_CANCELLED) {
                return;
            }
            throw new BizException(ResultCode.ORDER_STATUS_INVALID);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeUnpaidBySystem(Long orderId) {
        Order order = getById(orderId);
        if (order == null) {
            return false;
        }
        return tryCancelUnpaidAndRestore(order);
    }

    // ==================== 私有辅助 ====================

    /**
     * CAS {@code UNPAID → CANCELLED}，成功则按 orderNo 幂等回滚库存。
     *
     * @return true 表示本次关单成功；false 表示状态已非未付（已支付/已取消等）
     */
    private boolean tryCancelUnpaidAndRestore(Order order) {
        boolean updated = lambdaUpdate()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, STATUS_UNPAID)
                .set(Order::getStatus, STATUS_CANCELLED)
                .update();
        if (!updated) {
            return false;
        }
        // 回滚库存（库存+1 且把商品状态恢复为在售），传入 orderNo 保证幂等
        unwrap(productFeign.restoreStock(order.getProductId(), order.getOrderNo()));
        return true;
    }

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

    @Override
    public String seckill(Long buyerId, Long productId) {
        String stockKey = "seckill:stock:" + productId;
        String productKey = "seckill:product:" + productId;
        String resultKey = "seckill:result:" + productId + ":" + buyerId;

        // 1. 缓存预热
        preheatStock(productId, stockKey, productKey);

        // 2. 基本规则校验
        ProductDTO product = (ProductDTO) redisTemplate.opsForValue().get(productKey);
        if (product == null) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }
        if (product.getSaleType() == null || product.getSaleType() != 1) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "非秒杀商品不可秒杀下单");
        }
        if (buyerId.equals(product.getSellerId())) {
            throw new BizException(ResultCode.ORDER_CANNOT_BUY_OWN);
        }
        enforcePurchaseLimit(buyerId, productId, product);

        // 3. 重复秒杀检验 & 占位原子操作
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(resultKey, "queuing", java.time.Duration.ofMinutes(15));
        if (Boolean.FALSE.equals(acquired)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "请勿重复提交秒杀请求");
        }

        // 4. Redis 预扣库存
        Long stock = stringRedisTemplate.opsForValue().decrement(stockKey);
        if (stock == null || stock < 0) {
            stringRedisTemplate.opsForValue().increment(stockKey); // 还原
            stringRedisTemplate.delete(resultKey);
            throw new BizException(ResultCode.PRODUCT_STOCK_INSUFFICIENT);
        }

        // 5. 发送秒杀消息入队列
        try {
            String requestId = UUID.randomUUID().toString().replace("-", "");
            com.campus.order.dto.SeckillMessage seckillMessage =
                    new com.campus.order.dto.SeckillMessage(requestId, productId, buyerId);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SECKILL_EXCHANGE,
                    RabbitMQConfig.SECKILL_ROUTING_KEY,
                    seckillMessage);
        } catch (Exception e) {
            log.error("发送秒杀消息至 MQ 失败，启动 Redis 状态回滚. buyerId={}, productId={}", buyerId, productId, e);
            try {
                stringRedisTemplate.delete(resultKey);
            } catch (Exception ex) {
                log.error("删除 resultKey 异常", ex);
            }
            try {
                stringRedisTemplate.opsForValue().increment(stockKey);
            } catch (Exception ex) {
                log.error("回滚 stockKey 异常", ex);
            }
            throw new BizException(ResultCode.INTERNAL_ERROR.getCode(), "排队失败，请稍后重试");
        }

        return productId + "_" + buyerId;
    }

    private void preheatStock(Long productId, String stockKey, String productKey) {
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(stockKey)) || Boolean.FALSE.equals(redisTemplate.hasKey(productKey))) {
            String lockKey = "seckill:lock:preheat:" + productId;
            Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", java.time.Duration.ofSeconds(5));
            if (Boolean.TRUE.equals(acquired)) {
                try {
                    if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(stockKey)) || Boolean.FALSE.equals(redisTemplate.hasKey(productKey))) {
                        ProductDTO product = unwrap(productFeign.getProduct(productId));
                        if (product == null) {
                            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
                        }
                        if (product.getStatus() == null || product.getStatus() != 1) {
                            throw new BizException(ResultCode.PRODUCT_OFF_SHELF);
                        }
                        // 预热 stock 与 product 详情，TTL 24小时
                        stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(product.getStock()), java.time.Duration.ofHours(24));
                        redisTemplate.opsForValue().set(productKey, product, java.time.Duration.ofHours(24));
                    }
                } finally {
                    stringRedisTemplate.delete(lockKey);
                }
            } else {
                int retries = 5;
                try {
                    while (retries > 0 && (Boolean.FALSE.equals(stringRedisTemplate.hasKey(stockKey)) || Boolean.FALSE.equals(redisTemplate.hasKey(productKey)))) {
                        Thread.sleep(50);
                        retries--;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(stockKey)) || Boolean.FALSE.equals(redisTemplate.hasKey(productKey))) {
                    throw new BizException(ResultCode.INTERNAL_ERROR.getCode(), "系统繁忙，请稍后再试");
                }
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createSeckillOrder(String requestId, Long buyerId, Long productId) {
        if (requestId == null || requestId.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "秒杀请求缺少幂等标识");
        }
        Order existingOrder = lambdaQuery().eq(Order::getRequestId, requestId).one();
        if (existingOrder != null) {
            return existingOrder.getOrderNo();
        }
        ProductDTO product = unwrap(productFeign.getProduct(productId));
        if (product == null) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }

        String orderNo = OrderNoGenerator.generate();
        stockCompensationService.register(productId, orderNo);
        stockCompensationService.lockForOrderTransaction(orderNo);
        boolean deducted = false;
        try {
            // 扣减 DB 库存
            unwrap(productFeign.deductStock(productId, orderNo, true));
            deducted = true;

            // 创建订单并落库
            Order order = new Order();
            order.setOrderNo(orderNo);
            order.setRequestId(requestId);
            order.setProductId(productId);
            order.setProductTitle(product.getTitle());
            order.setPrice(product.getPrice());
            order.setBuyerId(buyerId);
            order.setSellerId(product.getSellerId());
            order.setStatus(STATUS_UNPAID);
            save(order);

            // 发送普通下单 RabbitMQ 通知
            OrderNotifyMessage notifyMessage = new OrderNotifyMessage(
                    order.getOrderNo(),
                    order.getProductId(),
                    order.getProductTitle(),
                    order.getPrice(),
                    order.getBuyerId(),
                    order.getSellerId()
            );
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.ORDER_EXCHANGE,
                    RabbitMQConfig.ORDER_NOTIFY_ROUTING_KEY,
                    notifyMessage);

            stockCompensationService.completeAfterCommit(orderNo);
            return order.getOrderNo();
        } catch (Exception e) {
            log.error("创建秒杀订单异常，启动远程库存回滚。buyerId={}, productId={}, orderNo={}", buyerId, productId, orderNo, e);
            if (!deducted && e instanceof BizException bizException && isDefinitiveNoDeduction(bizException)) {
                stockCompensationService.completeAfterCompletion(orderNo);
            }
            if (deducted) {
                try {
                    unwrap(productFeign.restoreStock(productId, orderNo));
                    stockCompensationService.completeAfterCompletion(orderNo);
                } catch (Exception ex) {
                    log.error("CRITICAL: 秒杀库存回滚失败，productId={}, orderNo={}", productId, orderNo, ex);
                }
            }
            throw e;
        }
    }

    private boolean isDefinitiveNoDeduction(BizException exception) {
        return exception.getCode() == ResultCode.PRODUCT_NOT_FOUND.getCode()
                || exception.getCode() == ResultCode.PRODUCT_OFF_SHELF.getCode()
                || exception.getCode() == ResultCode.PRODUCT_STOCK_INSUFFICIENT.getCode();
    }

    @Override
    public com.campus.order.dto.SeckillResultVO getSeckillResult(Long buyerId, Long productId) {
        String resultKey = "seckill:result:" + productId + ":" + buyerId;
        Object val = stringRedisTemplate.opsForValue().get(resultKey);
        if (val == null) {
            return new com.campus.order.dto.SeckillResultVO(-1, null);
        }
        String statusStr = val.toString();
        if ("queuing".equals(statusStr)) {
            return new com.campus.order.dto.SeckillResultVO(0, null);
        } else if ("failed".equals(statusStr)) {
            return new com.campus.order.dto.SeckillResultVO(-1, null);
        } else {
            return new com.campus.order.dto.SeckillResultVO(1, statusStr);
        }
    }

    private void enforcePurchaseLimit(Long buyerId, Long productId, ProductDTO product) {
        if (product.getIsTutorial() == null || product.getIsTutorial() != 1) {
            return;
        }
        Integer limit = product.getPurchaseLimit();
        if (limit == null || limit <= 0) {
            return;
        }
        long purchased = lambdaQuery()
                .eq(Order::getBuyerId, buyerId)
                .eq(Order::getProductId, productId)
                .ne(Order::getStatus, STATUS_CANCELLED)
                .count();
        if (purchased >= limit) {
            throw new BizException(ResultCode.ORDER_PURCHASE_LIMIT);
        }
    }

    @Override
    public SellerIncomeStatsView getSellerIncomeStats(Long sellerId) {
        requireMerchant(sellerId);
        List<Order> orders = lambdaQuery()
                .eq(Order::getSellerId, sellerId)
                .list();
        SellerIncomeStatsView stats = new SellerIncomeStatsView();
        stats.setTotalOrders(orders.size());
        BigDecimal revenue = BigDecimal.ZERO;
        long completed = 0;
        long pendingPayment = 0;
        long pendingShipment = 0;
        long cancelled = 0;
        for (Order order : orders) {
            Integer status = order.getStatus();
            if (status == null) {
                continue;
            }
            switch (status) {
                case STATUS_UNPAID -> pendingPayment++;
                case STATUS_PAID -> {
                    pendingShipment++;
                    if (order.getPrice() != null) {
                        revenue = revenue.add(order.getPrice());
                    }
                }
                case STATUS_DONE -> {
                    completed++;
                    if (order.getPrice() != null) {
                        revenue = revenue.add(order.getPrice());
                    }
                }
                case STATUS_CANCELLED -> cancelled++;
                default -> { }
            }
        }
        stats.setTotalRevenue(revenue);
        stats.setCompletedOrders(completed);
        stats.setPendingPaymentOrders(pendingPayment);
        stats.setPendingShipmentOrders(pendingShipment);
        stats.setCancelledOrders(cancelled);
        return stats;
    }

    @Override
    public com.campus.order.dto.SellerDashboardView getSellerDashboard(Long sellerId) {
        requireMerchant(sellerId);
        com.campus.order.dto.SellerDashboardView dashboard = new com.campus.order.dto.SellerDashboardView();
        SellerIncomeStatsView summary = getSellerIncomeStats(sellerId);
        dashboard.setSummary(summary);

        List<com.campus.order.dto.StatusCountPoint> breakdown = new java.util.ArrayList<>();
        breakdown.add(statusPoint(STATUS_UNPAID, "待付款", summary.getPendingPaymentOrders()));
        breakdown.add(statusPoint(STATUS_PAID, "待发货", summary.getPendingShipmentOrders()));
        breakdown.add(statusPoint(STATUS_DONE, "已完成", summary.getCompletedOrders()));
        breakdown.add(statusPoint(STATUS_CANCELLED, "已取消", summary.getCancelledOrders()));
        dashboard.setOrderStatusBreakdown(breakdown);

        java.time.LocalDate end = java.time.LocalDate.now();
        java.time.LocalDate start = end.minusDays(89);
        java.time.LocalDateTime startDt = start.atStartOfDay();
        List<Order> recentOrders = lambdaQuery()
                .eq(Order::getSellerId, sellerId)
                .ge(Order::getCreateTime, startDt)
                .in(Order::getStatus, STATUS_PAID, STATUS_DONE)
                .list();

        java.util.Map<java.time.LocalDate, com.campus.order.dto.DailyRevenuePoint> dailyMap = new java.util.TreeMap<>();
        for (java.time.LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            com.campus.order.dto.DailyRevenuePoint point = new com.campus.order.dto.DailyRevenuePoint();
            point.setDate(d.toString());
            point.setRevenue(BigDecimal.ZERO);
            point.setOrderCount(0);
            dailyMap.put(d, point);
        }
        for (Order order : recentOrders) {
            if (order.getCreateTime() == null) {
                continue;
            }
            java.time.LocalDate day = order.getCreateTime().toLocalDate();
            com.campus.order.dto.DailyRevenuePoint point = dailyMap.get(day);
            if (point == null) {
                continue;
            }
            point.setOrderCount(point.getOrderCount() + 1);
            if (order.getPrice() != null) {
                point.setRevenue(point.getRevenue().add(order.getPrice()));
            }
        }
        dashboard.setDailyRevenue(new java.util.ArrayList<>(dailyMap.values()));
        return dashboard;
    }

    private com.campus.order.dto.StatusCountPoint statusPoint(int status, String text, long count) {
        com.campus.order.dto.StatusCountPoint point = new com.campus.order.dto.StatusCountPoint();
        point.setStatus(status);
        point.setStatusText(text);
        point.setCount(count);
        return point;
    }

    private void requireMerchant(Long userId) {
        Result<Integer> result = userFeign.getUserRole(userId);
        if (result == null || result.getCode() != ResultCode.SUCCESS.getCode() || result.getData() == null) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        if (!UserRole.canAccessMerchantHub(result.getData())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
    }
}
