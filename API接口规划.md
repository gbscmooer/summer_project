# 校园二手交易平台 —— API 接口规划文档

> 配套文档：`架构设计.md`
> 原则：**全部真实接口、真实数据库，不使用任何 Mock 数据**。前端照此对接，后端照此实现。

---

## 一、设计规范

### 1.1 RESTful 约定

| 操作 | HTTP 方法 | 说明 |
|------|----------|------|
| 查询 | GET | 不改变数据 |
| 新增 | POST | 创建资源 |
| 全量更新 | PUT | 更新资源 |
| 部分更新 | PATCH | 改状态等 |
| 删除 | DELETE | 删除/下架 |

### 1.2 接口路径前缀

- 所有对外接口经过网关，统一前缀 `/api`，由网关去掉前缀转发。
- 例：前端请求 `GET /api/product/list` → 网关转发到 `campus-product` 的 `GET /product/list`。

### 1.3 统一响应格式（不可省略）

所有接口**无论成功失败**都返回这个结构：

```json
{
  "code": 200,
  "message": "success",
  "data": { }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | int | 业务状态码，见 1.5 |
| message | string | 提示信息 |
| data | object/array/null | 业务数据，失败时为 null |

> ⚠️ HTTP 状态码统一返回 200（除网关层鉴权失败返回 401），业务成败用 `code` 字段区分。

### 1.4 认证方式

- 登录成功后返回 JWT `token`。
- 需要登录的接口，请求头携带：`Authorization: Bearer {token}`。
- **网关统一校验** Token，解析出 `userId` 放进请求头 `X-User-Id` 透传给下游服务。
- 下游服务从 `X-User-Id` 取当前用户，**不再重复解析 Token**。

### 1.5 业务状态码表

| code | 含义 |
|------|------|
| 200 | 成功 |
| 400 | 参数错误 |
| 401 | 未登录 / Token 失效 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |
| 1001 | 用户名已存在 |
| 1002 | 用户名或密码错误 |
| 2001 | 商品不存在 |
| 2002 | 商品已下架/已售 |
| 2003 | 库存不足 |
| 3001 | 订单不存在 |
| 3002 | 不能购买自己的商品 |
| 3003 | 订单状态不允许此操作 |

### 1.6 分页参数约定

列表类接口统一用：

| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| pageNum | int | 1 | 页码 |
| pageSize | int | 10 | 每页条数 |

分页响应统一结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 100,
    "pageNum": 1,
    "pageSize": 10,
    "list": [ ]
  }
}
```

---

## 二、网关路由规划（campus-gateway:8080）

| 路由 | 转发目标服务 | 是否需要登录 |
|------|-------------|------------|
| `/api/user/**` | campus-user (8081) | 部分（见下表白名单） |
| `/api/product/**` | campus-product (8082) | 部分 |
| `/api/order/**` | campus-order (8083) | 是（全部需登录） |

**鉴权白名单（无需登录即可访问）：**
- `POST /api/user/register`
- `POST /api/user/login`
- `GET  /api/product/list`
- `GET  /api/product/search`
- `GET  /api/product/{id}`

---

## 三、用户服务 API（campus-user）

### 3.1 用户注册

```
POST /api/user/register
```

**请求体：**
```json
{
  "username": "zhangsan",
  "password": "123456",
  "nickname": "张三",
  "phone": "13800138000"
}
```

**响应：**
```json
{ "code": 200, "message": "注册成功", "data": { "userId": 1001 } }
```

**失败（用户名已存在）：**
```json
{ "code": 1001, "message": "用户名已存在", "data": null }
```

---

### 3.2 用户登录

```
POST /api/user/login
```

**请求体：**
```json
{ "username": "zhangsan", "password": "123456" }
```

**响应：**
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "userId": 1001,
    "nickname": "张三",
    "avatar": "https://..."
  }
}
```

---

### 3.3 获取当前登录用户信息

```
GET /api/user/info
请求头：Authorization: Bearer {token}
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": 1001,
    "username": "zhangsan",
    "nickname": "张三",
    "avatar": "https://...",
    "phone": "13800138000",
    "createTime": "2026-06-22 10:00:00"
  }
}
```

---

### 3.4 更新个人信息

```
PUT /api/user/info
请求头：Authorization: Bearer {token}
```

**请求体（仅传需要改的字段）：**
```json
{ "nickname": "新昵称", "avatar": "https://...", "phone": "13900139000" }
```

**响应：**
```json
{ "code": 200, "message": "更新成功", "data": null }
```

---

### 3.5 【内部接口】根据 ID 批量查询用户（供 OpenFeign 调用）

```
GET /user/batch?ids=1001,1002
```
> 内部接口，不经过网关、不对前端暴露。供订单服务查询买家/卖家信息。

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": [
    { "userId": 1001, "nickname": "张三", "phone": "13800138000" },
    { "userId": 1002, "nickname": "李四", "phone": "13900139000" }
  ]
}
```

---

## 四、商品服务 API（campus-product）

### 4.1 发布商品

```
POST /api/product
请求头：Authorization: Bearer {token}
```

**请求体：**
```json
{
  "title": "九成新高数教材",
  "description": "同济版高等数学，无笔记无划线",
  "price": 25.00,
  "images": "https://img1.jpg,https://img2.jpg",
  "category": "教材",
  "stock": 1
}
```

**响应：**
```json
{ "code": 200, "message": "发布成功", "data": { "productId": 5001 } }
```

> 后端逻辑：写 MySQL → 同步写 ES（或发 MQ 异步同步）。卖家 ID 取自 `X-User-Id`。

---

### 4.2 修改商品

```
PUT /api/product/{id}
请求头：Authorization: Bearer {token}
```
> 仅卖家本人可改（校验 `X-User-Id` == `seller_id`，否则返回 403）。

**请求体**：同发布。
**响应：**
```json
{ "code": 200, "message": "修改成功", "data": null }
```

---

### 4.3 下架/删除商品

```
DELETE /api/product/{id}
请求头：Authorization: Bearer {token}
```
> 逻辑删除（status 改为 0），仅卖家本人可操作。同步更新/删除 ES 索引、清 Redis 缓存。

**响应：**
```json
{ "code": 200, "message": "已下架", "data": null }
```

---

### 4.4 商品详情（无需登录）

```
GET /api/product/{id}
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "productId": 5001,
    "title": "九成新高数教材",
    "description": "同济版高等数学，无笔记无划线",
    "price": 25.00,
    "images": ["https://img1.jpg", "https://img2.jpg"],
    "category": "教材",
    "sellerId": 1001,
    "sellerNickname": "张三",
    "status": 1,
    "stock": 1,
    "viewCount": 38,
    "createTime": "2026-06-20 14:30:00"
  }
}
```

> 后端逻辑：先查 Redis（`product:detail:{id}`），命中直接返回；未命中查 MySQL 回填缓存。浏览量 `INCR`。

---

### 4.5 商品列表（无需登录，分页+分类）

```
GET /api/product/list?pageNum=1&pageSize=10&category=教材
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 56,
    "pageNum": 1,
    "pageSize": 10,
    "list": [
      {
        "productId": 5001,
        "title": "九成新高数教材",
        "price": 25.00,
        "cover": "https://img1.jpg",
        "category": "教材",
        "status": 1,
        "createTime": "2026-06-20 14:30:00"
      }
    ]
  }
}
```

---

### 4.6 商品搜索（无需登录，走 ES）

```
GET /api/product/search?keyword=高数&category=教材&minPrice=0&maxPrice=100&sort=price_asc&pageNum=1&pageSize=10
```

| 参数 | 说明 |
|------|------|
| keyword | 关键词（标题+描述分词搜索） |
| category | 分类过滤（可选） |
| minPrice/maxPrice | 价格区间（可选） |
| sort | 排序：`time_desc`(默认) / `price_asc` / `price_desc` |

**响应**：结构同 4.5 列表。
> 后端逻辑：走 Elasticsearch 查询，不查 MySQL。

---

### 4.7 我发布的商品

```
GET /api/product/my?pageNum=1&pageSize=10
请求头：Authorization: Bearer {token}
```
> 查 `seller_id = X-User-Id` 的商品。
**响应**：结构同 4.5 列表（含各状态商品）。

---

### 4.8 【内部接口】查询商品（供 OpenFeign 调用）

```
GET /product/inner/{id}
```
**响应**：单个商品完整信息（同 4.4 data）。

---

### 4.9 【内部接口】扣减库存（供 OpenFeign 调用）

```
POST /product/inner/{id}/deduct
```
> 订单服务下单时调用。校验在售 + 库存，扣减成功返回 true。

**响应：**
```json
{ "code": 200, "message": "success", "data": true }
```
**失败：**
```json
{ "code": 2003, "message": "库存不足", "data": false }
```

---

## 五、订单服务 API（campus-order）

> 全部接口需要登录。

### 5.1 创建订单（下单）

```
POST /api/order
请求头：Authorization: Bearer {token}
```

**请求体：**
```json
{ "productId": 5001 }
```

**响应：**
```json
{
  "code": 200,
  "message": "下单成功",
  "data": {
    "orderId": 9001,
    "orderNo": "20260622103015001",
    "productId": 5001,
    "productTitle": "九成新高数教材",
    "price": 25.00,
    "status": 0
  }
}
```

**后端完整链路（答辩重点）：**
1. 取当前用户 `buyerId = X-User-Id`。
2. **OpenFeign** 调商品服务 `GET /product/inner/{id}` → 校验商品存在、在售。
3. 校验 `buyerId != sellerId`（不能买自己的，否则 `code:3002`）。
4. **OpenFeign** 调 `POST /product/inner/{id}/deduct` 扣库存（失败则 `code:2003`）。
5. 写订单表（冗余 `product_title`、`seller_id`）。
6. **发 RabbitMQ 消息** → 异步通知卖家"你的商品被拍下"。
7. 返回订单信息。

---

### 5.2 订单详情

```
GET /api/order/{id}
请求头：Authorization: Bearer {token}
```
> 仅订单的买家或卖家可看，否则 403。

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "orderId": 9001,
    "orderNo": "20260622103015001",
    "productId": 5001,
    "productTitle": "九成新高数教材",
    "price": 25.00,
    "buyerId": 1002,
    "buyerNickname": "李四",
    "sellerId": 1001,
    "sellerNickname": "张三",
    "status": 0,
    "statusText": "待付款",
    "createTime": "2026-06-22 10:30:15"
  }
}
```
> buyerNickname/sellerNickname 通过 OpenFeign 调用户服务 `/user/batch` 获取。

---

### 5.3 我买到的订单（买家视角）

```
GET /api/order/buyer?status=&pageNum=1&pageSize=10
请求头：Authorization: Bearer {token}
```
| 参数 | 说明 |
|------|------|
| status | 订单状态过滤，空=全部 |

**响应**：分页列表，结构同 5.2 data 的精简版。

---

### 5.4 我卖出的订单（卖家视角）

```
GET /api/order/seller?status=&pageNum=1&pageSize=10
请求头：Authorization: Bearer {token}
```
**响应**：分页列表。

---

### 5.5 支付订单

```
PUT /api/order/{id}/pay
请求头：Authorization: Bearer {token}
```
> 仅买家本人，状态须为 0（待付款）→ 改为 1（已付款）。模拟支付，无需真实对接支付。

**响应：**
```json
{ "code": 200, "message": "支付成功", "data": null }
```

---

### 5.6 确认收货

```
PUT /api/order/{id}/confirm
请求头：Authorization: Bearer {token}
```
> 仅买家，状态须为 1（已付款）→ 改为 2（已完成）。同时把商品状态改为已售（OpenFeign 通知商品服务）。

**响应：**
```json
{ "code": 200, "message": "交易完成", "data": null }
```

---

### 5.7 取消订单

```
PUT /api/order/{id}/cancel
请求头：Authorization: Bearer {token}
```
> 买家可取消待付款订单，状态 0 → 3（已取消）。同时**回滚库存**（OpenFeign 调商品服务恢复库存）。

**响应：**
```json
{ "code": 200, "message": "已取消", "data": null }
```

---

## 六、（可选加分）限时抢购接口 —— MQ 削峰演示

> 用于 Day6 流量削峰 + Jmeter 压测演示。

### 6.1 发起抢购

```
POST /api/order/seckill
请求头：Authorization: Bearer {token}
请求体：{ "productId": 5001 }
```

**后端链路（削峰）：**
1. Redis 预扣库存（`DECR`），不足直接返回"已抢光"。
2. 抢购请求**发 MQ 排队**，立即返回"排队中"。
3. MQ 消费者**匀速**消费 → 真正落库下单。
4. 前端轮询订单结果。

**响应：**
```json
{ "code": 200, "message": "排队中，请稍候", "data": { "queueId": "xxx" } }
```

---

## 七、接口开发顺序（保证不 Mock、真实联调）

> 关键原则：**后端接口先行，前端永远对接真实接口**。每完成一个接口立即用 Apifox/Postman 验证真实返回，再写前端。

| 顺序 | 接口组 | 依赖 |
|------|--------|------|
| 1 | 用户：注册、登录、info | MySQL + JWT |
| 2 | 商品：发布、详情、列表、我的 | MySQL |
| 3 | 网关路由 + JWT 校验 | Nacos + Gateway |
| 4 | 订单：下单（含 Feign 调用） | OpenFeign |
| 5 | 商品详情接入 Redis 缓存 | Redis |
| 6 | 商品搜索接入 ES | Elasticsearch |
| 7 | 下单接入 RabbitMQ 通知 | RabbitMQ |
| 8 | 订单：支付/收货/取消 | - |
| 9 | （可选）抢购削峰 | Redis + MQ |

---

## 八、联调与验证工具

- **接口测试**：Apifox / Postman，按本文档建好接口集合，每个接口存真实示例。
- **数据库**：每个接口跑通后，直接查 MySQL 确认数据真实落库。
- **Redis**：用 `RedisInsight` 或 `redis-cli` 查缓存 key 是否真实写入。
- **ES**：`GET http://localhost:9200/product/_search` 确认索引真实建立。
- **MQ**：RabbitMQ 管理台（15672）确认消息真实进出队列。
- **前端**：axios `baseURL` 直接指向网关 `http://localhost:8080/api`，无任何 mock 拦截。

---

*文档版本 v1.0 ｜ 配合 架构设计.md 使用 ｜ 接口随开发微调，改动同步更新本文档*
