# Document

## 架构模块

| 模块 | 端口 | 职责 | 依赖 |
| --- | --- | --- | --- |
| `campus-gateway` | 8080 | `/api/**` 路由、JWT 鉴权、`X-User-Id` 注入、CORS | Nacos |
| `campus-user` | 8081 | 注册、登录、用户信息、角色、用户简要信息内部查询 | MySQL、Nacos |
| `campus-product` | 8082 | 商品发布、图片存储、搜索、AI 自然语言检索、AI 多图发布草稿、库存内部接口 | MySQL、Redis、Elasticsearch、Nacos、OpenFeign、OpenAI-compatible API |
| `campus-order` | 8083 | 下单、订单查询、状态流转、通知、秒杀削峰 | MySQL、Redis、RabbitMQ、Nacos、OpenFeign |
| `campus-trade-web` | 5173/80 | Vue 前端、Nginx 静态托管 | Gateway |

## 数据流

1. 浏览器请求 `/api/**`。
2. 网关移除客户端传入的 `X-User-Id`。
3. 白名单接口直接转发；非白名单接口校验 `Authorization: Bearer <token>`。
4. 网关解析 JWT 中的 `userId`，向下游注入 `X-User-Id`。
5. 下游服务执行业务逻辑并返回统一响应。
6. 前端 axios 拦截器处理 `code === 200`、`code === 401` 和其它业务错误。

## AI 数据流

### 买家自然语言找商品

1. 前端向 `POST /api/ai/search` 提交自然语言和返回数量（需登录）。
2. `AiController` 只做校验与响应封装，`AiAssistantService` 调用模型提取关键词、分类、预算和排序。
3. AI Core 调用现有 `ProductService.search`，结果只来自 MySQL/Elasticsearch 中真实在售商品。
4. 返回意图解释、命中总数、结果价格区间和商品列表；模型不生成商品。

### 卖家多图生成发布草稿

1. 已登录卖家向 `POST /api/product/image/upload` 上传 1～5 张 JPG/PNG/WEBP 图片，单张最大 8MB；元数据记录上传者。
2. 图片保存在商品服务配置目录，返回 `/api/product/image/{filename}`；图片读取公开，上传/删除/AI 草稿需鉴权且校验归属。
3. 前端向 `POST /api/ai/listing-draft` 提交已上传图片地址与可选备注。
4. AI Core 从站内存储读取本人图片并调用视觉模型（最多 3 张），生成标题、分类、成色、描述、关键词和识别告警。
5. AI Core 使用关键词查询站内同类在售商品，以价格四分位数和成色系数生成建议价；无同类数据时才使用模型参考价。
6. 草稿回填发布表单，卖家检查或修改后再调用原 `POST /api/product` 发布。

### 管理员配置 AI API

1. 用户表 `role=1` 为管理员；管理员须通过受控离线流程授权，不提供默认账号。
2. 管理员登录后在「设置」页配置 Base URL、Model、API Key、超时与是否支持视觉。
3. `GET/POST /api/admin/ai-config` 读写配置；Key 以 AES-GCM 密文保存，响应脱敏。
4. 配置按请求读取数据库，多副本无需本地缓存失效；启用覆盖后立即生效，关闭后回退环境变量。Key 与规范化 API 地址绑定，地址变化会清空未重新提交的 Key。

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
| 4001 | AI 服务未配置 |
| 4002 | AI 返回内容无法解析 |
| 4003 | AI 商品图片不存在或格式不支持 |
| 4004 | 当前 AI 模型不支持图片识别 |

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
| POST | `/api/product/reindex` | 管理员重建 ES 索引 | 需管理员 | 重建数量 |
| POST | `/api/product/image/upload` | 上传商品实拍图 | multipart: `files`，1～5张，需登录 | `images` 图片地址数组 |
| POST | `/api/product/image/delete` | 删除本人实拍图 | body: `url`，需登录 | null |
| GET | `/api/product/image/{filename}` | 读取商品图片 | path: `filename` | 图片二进制 |

### AI 助手

| 方法 | 路径 | 功能 | 参数 | 返回 |
| --- | --- | --- | --- | --- |
| POST | `/api/ai/search` | 自然语言查找真实在售商品 | body: `query`, `pageSize`(1～20)；需登录 | `summary`, `intent`, `total`, `priceLow`, `priceHigh`, `products` |
| POST | `/api/ai/listing-draft` | 多图生成待确认发布草稿与建议价 | body: `images`(1～5个站内地址), `notes`; 需登录 | `title`, `description`, `category`, `condition`, `suggestedPrice`, `marketPriceLow`, `marketPriceHigh`, `comparableCount`, `pricingBasis`, `confidence`, `warnings` |

### 管理端

| 方法 | 路径 | 功能 | 参数 | 返回 |
| --- | --- | --- | --- | --- |
| GET | `/api/admin/ai-config` | 读取 AI 配置（Key 脱敏） | 需管理员 | `enabled`, `baseUrl`, `apiKeyMasked`, `model`, `timeoutSeconds`, `supportsVision`, `activeSource` |
| POST | `/api/admin/ai-config` | 保存 AI 配置（热更新） | body: `enabled`, `baseUrl`, `apiKey`, `model`, `timeoutSeconds`, `supportsVision`；Key 仅在地址未变化且绑定一致时可保留 | 同上 |

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
| GET | `/user/batch?ids=1,2` | `campus-order`、`campus-product` | 携带 `X-Internal-Token`，批量查询用户 ID 与昵称 |
| GET | `/product/inner/{id}` | `campus-order` | 携带 `X-Internal-Token`，查询单个商品 |
| POST | `/product/inner/{id}/deduct` | `campus-order` | 携带 `X-Internal-Token`；query: `orderNo` 必填并用于扣减幂等，`preserveSeckillCache` 可选 |
| POST | `/product/inner/{id}/restore` | `campus-order` | 携带 `X-Internal-Token`，按订单号恢复库存 |

## 数据表

| 表 | 模块 | 说明 |
| --- | --- | --- |
| `t_user` | 用户服务 | 用户账号、密码哈希、昵称、头像、手机号 |
| `t_product` | 商品服务 | 商品信息、卖家、状态、库存、浏览量 |
| `t_order` | 订单服务 | 订单流水、秒杀请求幂等 ID、买家、卖家、商品冗余信息、状态 |
| `t_notification` | 订单服务 | 下单通知、已读状态、关联订单号 |
| `t_stock_restore_log` | 商品服务 | 以商品 ID 与订单号唯一约束保证库存恢复幂等 |
| `t_stock_deduction_log` | 商品服务 | 以商品 ID 与订单号唯一约束保证库存扣减幂等，并阻止无扣减流水的恢复 |
| `t_stock_compensation_task` | 订单服务 | 跨服务扣减的持久补偿意图；活动订单事务持行锁，重试器用 `FOR UPDATE SKIP LOCKED` 仅处理已释放任务 |

## 中间件数据

| 中间件 | Key/索引/队列 | 用途 |
| --- | --- | --- |
| Redis | `product:detail:{id}` | 商品详情缓存 |
| Redis | `seckill:stock:{id}` | 秒杀库存预扣 |
| Redis | `seckill:result:{productId}:{buyerId}` | 秒杀排队结果 |
| Elasticsearch | `product` | 商品搜索索引 |
| RabbitMQ | `order.notify.queue` | 下单通知消费 |
| RabbitMQ | `seckill.queue` | 秒杀异步下单消费 |

## AI 配置

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `AI_API_KEY` | 空 | 环境变量兜底；为空且管理端未配置时 AI 接口返回业务码 4001 |
| `AI_BASE_URL` | `https://api.openai.com/v1` | OpenAI-compatible API 根地址 |
| `AI_MODEL` | `gpt-4.1-mini` | 支持 JSON 输出和图片理解的模型 |
| `AI_SUPPORTS_VISION` | `true` | 模型是否接受 `image_url`；DeepSeek `deepseek-v4-flash` 应设为 `false` |
| `AI_TIMEOUT_SECONDS` | `60` | 模型请求超时秒数，运行时限制为 5～85 秒 |
| `AI_DAILY_USER_LIMIT` | `100` | 每位用户每日 AI 请求上限；Redis 原子计数，超额返回 429 业务码 |
| `AI_GLOBAL_CONCURRENCY` | `8` | Redis 共享的跨实例 AI 并发上限；单实例另限制为 4 |
| `PRODUCT_IMAGE_DIR` | `./data/product-images` | 商品实拍图持久化目录 |
| `AI_CONFIG_ENCRYPTION_KEY` | 无 | 管理端 AI Key 的 AES-GCM 主密钥，至少 32 字节，缺失拒绝启动 |

## 安全部署配置

| 环境变量 | 说明 |
| --- | --- |
| `JWT_SECRET` | JWT HMAC 密钥，至少 32 字节；网关与用户服务必须一致 |
| `JWT_EXPIRATION_MINUTES` | JWT 有效期分钟数，5～1440，默认 120 |
| `INTERNAL_API_TOKEN` | 内部 Feign 接口令牌，至少 32 字节；用户、商品、订单服务必须一致 |
| `MYSQL_ROOT_PASSWORD` / `MYSQL_PASSWORD` | Compose 必填数据库随机密码 |
| `REDIS_PASSWORD` | Compose 必填 Redis 随机密码 |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | Compose 必填 RabbitMQ 凭据 |
| `TRUSTED_PROXY_CIDR` | HTTPS 反向代理/LB 的实际源 CIDR；用于可信 real-IP 解析，禁止 `0.0.0.0/0` |
| `CORS_ALLOWED_ORIGINS` | 逗号分隔的精确 HTTPS 前端 Origin；同源部署可留空 |

生产 Compose 默认仅 Web 绑定宿主机回环地址，业务服务和中间件仅在内部网络可达。同机代理使用回环绑定；外部 LB 使用实例私网 IP 并由安全组仅允许 LB 源 CIDR。

管理员可在前端「设置 → AI 接口配置」覆盖上述参数（写入 `t_ai_config`，立即生效）。已有数据库可执行 `sql/migrate-ai-admin.sql` 补齐 `role` 与相关表。

AI 网关路由响应超时为 90 秒；模型超时不超过 85 秒。AI Core 全局最多 4 个并发流程，图片完整解码最多 2 个并发且不超过 1000 万像素；Nginx API 读取/发送超时为 95 秒，上传请求体上限为 40MB。
