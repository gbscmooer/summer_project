# Document

## 架构模块

| 模块 | 端口 | 职责 | 依赖 |
| --- | --- | --- | --- |
| `campus-gateway` | 8080 | `/api/**` 路由、JWT 鉴权、`X-User-Id` 注入、CORS | Nacos |
| `campus-user` | 8081 | 注册、登录、用户信息、用户简要信息内部查询 | MySQL、Nacos |
| `campus-product` | 8082 | 商品发布、修改、下架、列表、详情、搜索、库存内部接口 | MySQL、Redis、Elasticsearch、Nacos、OpenFeign |
| `campus-order` | 8083 | 下单、订单查询、状态流转、通知、秒杀削峰 | MySQL、Redis、RabbitMQ、Nacos、OpenFeign |
| `campus-trade-web` | 5173/80 | Vue 前端、Nginx 静态托管 | Gateway |

## 数据流

1. 浏览器请求 `/api/**`。
2. 网关移除客户端传入的 `X-User-Id`。
3. 白名单接口直接转发；非白名单接口校验 `Authorization: Bearer <token>`。
4. 网关解析 JWT 中的 `userId`，向下游注入 `X-User-Id`。
5. 下游服务执行业务逻辑并返回统一响应。
6. 前端 axios 拦截器处理 `code === 200`、`code === 401` 和其它业务错误。

## 统一响应

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

## 状态码

| code | 含义 |
| --- | --- |
| 200 | 成功 |
| 400 | 参数错误 |
| 401 | 未登录或 Token 失效 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |
| 1001 | 用户名已存在 |
| 1002 | 用户名或密码错误 |
| 2001 | 商品不存在 |
| 2002 | 商品已下架或已售 |
| 2003 | 库存不足 |
| 3001 | 订单不存在 |
| 3002 | 不能购买自己的商品 |
| 3003 | 订单状态不允许此操作 |

## 外部接口

### 用户服务

| 方法 | 路径 | 功能 | 参数 | 返回 |
| --- | --- | --- | --- | --- |
| POST | `/api/user/register` | 注册 | body: `username`, `password`, `nickname`, `phone` | `userId` |
| POST | `/api/user/login` | 登录 | body: `username`, `password` | `token`, `userId`, `nickname`, `avatar` |
| GET | `/api/user/info` | 当前用户信息 | header: `Authorization` | `userId`, `username`, `nickname`, `avatar`, `phone`, `createTime` |
| POST | `/api/user/info` | 更新用户信息 | body: `nickname`, `avatar`, `phone` | `null` |

### 商品服务

| 方法 | 路径 | 功能 | 参数 | 返回 |
| --- | --- | --- | --- | --- |
| POST | `/api/product` | 发布商品 | body: `title`, `description`, `price`, `images`, `category`, `stock` | `productId` |
| POST | `/api/product/{id}/update` | 修改商品 | path: `id`; body 同发布 | `null` |
| POST | `/api/product/{id}/delete` | 下架商品 | path: `id` | `null` |
| GET | `/api/product/{id}` | 商品详情 | path: `id` | `productId`, `title`, `description`, `price`, `images`, `category`, `sellerId`, `sellerNickname`, `status`, `stock`, `viewCount`, `createTime` |
| GET | `/api/product/list` | 商品分页列表 | query: `pageNum`, `pageSize`, `category` | `total`, `pageNum`, `pageSize`, `list` |
| GET | `/api/product/search` | 商品搜索 | query: `keyword`, `category`, `minPrice`, `maxPrice`, `sort`, `pageNum`, `pageSize` | `total`, `pageNum`, `pageSize`, `list` |
| GET | `/api/product/my` | 我的商品 | query: `pageNum`, `pageSize` | `total`, `pageNum`, `pageSize`, `list` |
| POST | `/api/product/reindex` | 重建 ES 索引 | 无 | 重建数量 |

### 订单服务

| 方法 | 路径 | 功能 | 参数 | 返回 |
| --- | --- | --- | --- | --- |
| POST | `/api/order` | 创建订单 | body: `productId` | `orderId`, `orderNo`, `productId`, `productTitle`, `price`, `status` |
| GET | `/api/order/{id}` | 订单详情 | path: `id` | `orderId`, `orderNo`, `productId`, `productTitle`, `price`, `buyerId`, `buyerNickname`, `sellerId`, `sellerNickname`, `status`, `statusText`, `createTime` |
| GET | `/api/order/buyer` | 买家订单列表 | query: `status`, `pageNum`, `pageSize` | `total`, `pageNum`, `pageSize`, `list` |
| GET | `/api/order/seller` | 卖家订单列表 | query: `status`, `pageNum`, `pageSize` | `total`, `pageNum`, `pageSize`, `list` |
| POST | `/api/order/{id}/pay` | 支付订单 | path: `id` | `null` |
| POST | `/api/order/{id}/confirm` | 确认收货 | path: `id` | `null` |
| POST | `/api/order/{id}/cancel` | 取消订单 | path: `id` | `null` |
| POST | `/api/order/seckill` | 秒杀下单入队 | body: `productId` | `queueId` |
| GET | `/api/order/seckill/result/{productId}` | 查询秒杀结果 | path: `productId` | `status`, `orderNo` |

### 通知服务

| 方法 | 路径 | 功能 | 参数 | 返回 |
| --- | --- | --- | --- | --- |
| GET | `/api/order/notification/list` | 通知列表 | query: `pageNum`, `pageSize` | `total`, `pageNum`, `pageSize`, `list` |
| GET | `/api/order/notification/unread-count` | 未读数量 | 无 | 数量 |
| POST | `/api/order/notification/{id}/read` | 标记单条已读 | path: `id` | `null` |
| POST | `/api/order/notification/read-all` | 全部已读 | 无 | `null` |

## 内部接口

| 方法 | 路径 | 调用方 | 功能 |
| --- | --- | --- | --- |
| GET | `/user/batch?ids=1,2` | `campus-order`、`campus-product` | 批量查询用户简要信息 |
| GET | `/product/inner/{id}` | `campus-order` | 查询单个商品 |
| POST | `/product/inner/{id}/deduct` | `campus-order` | 扣减库存；query: `preserveSeckillCache` 可选 |
| POST | `/product/inner/{id}/restore` | `campus-order` | 恢复库存 |

## 数据表

| 表 | 模块 | 说明 |
| --- | --- | --- |
| `t_user` | 用户服务 | 用户账号、密码哈希、昵称、头像、手机号 |
| `t_product` | 商品服务 | 商品信息、卖家、状态、库存、浏览量 |
| `t_order` | 订单服务 | 订单流水、买家、卖家、商品冗余信息、状态 |
| `t_notification` | 订单服务 | 下单通知、已读状态、关联订单号 |

## 中间件数据

| 中间件 | Key/索引/队列 | 用途 |
| --- | --- | --- |
| Redis | `product:detail:{id}` | 商品详情缓存 |
| Redis | `seckill:stock:{id}` | 秒杀库存预扣 |
| Redis | `seckill:result:{productId}:{buyerId}` | 秒杀排队结果 |
| Elasticsearch | `product` | 商品搜索索引 |
| RabbitMQ | `order.notify.queue` | 下单通知消费 |
| RabbitMQ | `seckill.queue` | 秒杀异步下单消费 |
