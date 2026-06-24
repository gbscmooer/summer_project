# 订单与通知服务文档 (campus-order)

`campus-order` 微服务负责常规下单与秒杀削峰下单、订单状态流转与超时取消、服务间远程 Feign 调用，以及基于 RabbitMQ 异步生成的通知消息推送。

---

## 1. 数据库物理表设计

### 1.1 订单表 (`t_order`)
| 字段名 | 类型 | 说明 |
| :--- | :--- | :--- |
| id | bigint PK | 订单ID，自增 |
| order_no | varchar(32) | 订单流水号，通过分布式随机规则生成 |
| product_id | bigint | 购买商品 ID |
| product_title | varchar(100) | 冗余的商品标题（避免高频跨模块关联查询） |
| price | decimal(10,2) | 实际成交金额 |
| buyer_id | bigint | 买家 ID |
| seller_id | bigint | 卖家 ID |
| status | tinyint | 状态：0-待付款，1-已付款，2-已完成，3-已取消 |
| create_time | datetime | 下单创建时间 |

### 1.2 通知表 (`t_notification`)
| 字段名 | 类型 | 说明 |
| :--- | :--- | :--- |
| id | bigint PK | 通知ID，自增 |
| user_id | bigint | 接收人 ID（一般为卖家） |
| type | varchar(50) | 通知类型（如 `ORDER_CREATED`） |
| title | varchar(100) | 通知标题 |
| content | varchar(500) | 通知内容正文 |
| order_no | varchar(32) | 关联的订单流水号（用于页面跳转） |
| is_read | tinyint | 是否已读：0-未读，1-已读 |
| create_time | datetime | 通知推送时间 |

---

## 2. 中间件与远程协作设计

### 2.1 OpenFeign 远程调用
本服务在交易环节大量利用 Feign 实现与 `campus-user` 和 `campus-product` 之间的数据同步：
- `ProductFeignClient`：获取商品详情、下单预占扣减库存、订单取消回滚库存。
- `UserFeignClient`：批量传入 `buyerId` 与 `sellerId` 获取用户昵称，展示订单详情与买卖列表。

### 2.2 RabbitMQ 消息队列拓扑
系统注册了如下交换机和队列：
1. **订单状态事件消息 (Order Notification Queue)**：
   * **交换机**：`order.exchange` (Topic 模式)
   * **路由键**：`order.notify`
   * **绑定队列**：`order.notify.queue`
   * **消费者**：`OrderNotificationConsumer`。收到消息后往 `t_notification` 插入通知记录，提醒卖家商品已被下单。
2. **秒杀异步下单排队 (Seckill Queue)**：
   * **交换机**：`seckill.exchange` (Topic 模式)
   * **路由键**：`seckill.order`
   * **绑定队列**：`seckill.queue`
   * **消费者**：`SeckillConsumer`。匀速拉取排队消息，调用本地 `createSeckillOrder` 实现数据库下单与扣减库存。

---

## 3. 秒杀抢购高性能削峰方案 (Seckill Flow)

在高并发场景下，直接调用常规下单直冲数据库会导致宕机。因此本微服务实现了两阶段秒杀架构：

```text
[用户请求] 
   │
   ▼
1. 校验与预热 ──► [Redis 缓存] ──► 2. 重复秒杀检验 ──► 3. 预扣库存 (DECR) 
                                                              │ (库存充足)
                                                              ▼
5. 轮询订单状态 ◄── 6. 客户端 ◄── 返回 "排队中" ◄── 4. 投递消息入 RabbitMQ 队列
                                                              │
                                                              ▼
                                                   [SeckillConsumer] 
                                                              │ (匀速消费)
                                                              ▼
                                                   7. MySQL 写入与 Feign 扣 DB
```

### 3.1 一阶段：Redis 内存预扣
- 商品库存和基本详情启动时或首次秒杀时自动预热入缓存（`seckill:stock:{id}` 与 `seckill:product:{id}`）。
- 拦截买家购买自己商品的行为。
- 校验重复秒杀：在 Redis 中查找 `seckill:result:{productId}:{buyerId}`，若存在说明已在队列或已下单，防止刷单。
- 原子预扣：运行 `DECR seckill:stock:{productId}`。若扣完后小于 0 证明售罄，立即返回“库存不足”（不进入 MQ 且无 DB 交互）。
- 库存充足时，将排队标记写入 Redis（状态设为 `queuing`），并将包含 `buyerId` 和 `productId` 的轻量级消息投递进 RabbitMQ，立即向前端返回响应。

### 3.2 二阶段：MQ 异步匀速写入
- `SeckillConsumer` 监听消息，使用本地事务执行 `createSeckillOrder`：
  - 调用 `productFeign.deductStock` 扣减 MySQL 中的真实库存。
  - MySQL 写入 `t_order` 订单记录，状态设为 0。
  - 下单成功后将 Redis 排队标记状态更新为正式订单号。如果中途发生异常，执行库存回滚，并将 Redis 状态改为 `failed`。

---

## 4. 对外 API 接口

### 4.1 常规下单 (需登录)
- **接口**：`POST /api/order`
- **逻辑**：Feign 调商品校验 -> 校验非自买 -> Feign 扣减 DB 库存 -> 写入订单表 -> 发送 RabbitMQ 通知提醒卖家。

### 4.2 订单详情 (需登录且仅买家/卖家)
- **接口**：`GET /api/order/{id}`
- **逻辑**：直查订单表，通过 Feign 批量查询昵称并渲染响应。

### 4.3 买家与卖家订单列表 (需登录)
- **接口**：`GET /api/order/buyer` / `GET /api/order/seller`

### 4.4 订单状态流转接口 (需登录)
- **支付订单**：`PUT /api/order/{id}/pay` (待付款状态改为已付款)
- **确认收货**：`PUT /api/order/{id}/confirm` (已付款状态改为已完成，并触发 Feign 修改商品为已售)
- **取消订单**：`PUT /api/order/{id}/cancel` (取消待付款订单，流转状态并 Feign 远程回滚商品库存)

### 4.5 高并发秒杀下单 (需登录)
- **接口**：`POST /api/order/seckill`
- **逻辑**：Redis 预扣与消息投递，详见第三章。
- **返回**：快速返回排队状态及排队标识 ID。

### 4.6 轮询秒杀结果 (需登录)
- **接口**：`GET /api/order/seckill/result?productId={id}`
- **返回**：`status` (0-排队中，1-秒杀成功，-1-秒杀失败) 与 `orderNo` (秒杀成功后的正式订单号)。

### 4.7 卖家通知 API 组 (需登录)
- **获取通知列表**：`GET /api/order/notification/list` (分页，最新在前)
- **获取未读通知数**：`GET /api/order/notification/unread-count` (用于前台小红点)
- **标记已读**：`PUT /api/order/notification/{id}/read`
- **全部已读**：`PUT /api/order/notification/read-all`
