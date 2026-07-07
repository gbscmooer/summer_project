# 校园二手交易平台 — 稳定性 / 运行时风险审计评估

> **文档类型**：稳定性与运行时风险审计（非网络安全渗透测试报告）  
> **审计日期**：2026-06-25  
> **代码基准**：`/Users/katisarrow/summer` 仓库当前工作区（含未提交改动）

---

## 1. 执行摘要

### 1.1 审计范围

| 层级 | 模块 | 说明 |
|------|------|------|
| 公共库 | `campus-common` | JWT 工具、统一异常与 Result 信封 |
| 网关 | `campus-gateway` | 路由、鉴权过滤器 |
| 业务服务 | `campus-user` / `campus-product` / `campus-order` | 用户、商品、订单及 MQ 消费者 |
| 前端 | `campus-trade-web` | Vue3 + Axios + Pinia |
| 编排 | 根目录 `docker-compose.yml`、`sql/init.sql` | 全栈容器化部署 |

### 1.2 审计方法

1. **静态代码审查**：阅读各模块 Controller / Service / Filter / Listener / Mapper 及配置文件。  
2. **变更对照**：对照当前 `git diff` 中与稳定性相关的近期改动（JwtUtil、AuthGlobalFilter、OrderServiceImpl 补偿、ProductServiceImpl 库存、SeckillConsumer、MetaObjectHandler 等）。  
3. **部署路径验证**：确认 Maven 多模块构建结构与 Docker Compose 服务依赖关系。  

未执行：压力测试、混沌工程、生产环境渗透。

### 1.3 总体结论

**高风险项（鉴权越权、订单-库存不一致、秒杀结果误标失败）已在本次及近期改动中得到实质性修复。** 当前代码在常规下单、取消回滚、秒杀异步落库等核心链路上具备补偿与幂等意识，网关鉴权不再阻塞 Netty 事件循环。

**仍存在的开放项**主要集中在：内部 Feign 接口无应用层鉴权（依赖网络隔离）、JWT 密钥硬编码、ES/MySQL 最终一致、秒杀 Redis 与 DB 双轨库存漂移、运维类接口权限不足、前端未对接秒杀 API 等——多为中低优先级，适合答辩/演示环境接受，生产上线前需进一步加固。

**综合评级**：演示 / 课程答辩环境 **可接受**；生产环境 **需完成开放项中 Medium 级别加固后再上线**。

---

## 2. 各模块发现项

### 2.1 campus-common

| Severity | Location | Finding | Status |
|----------|----------|---------|--------|
| **High** | `JwtUtil.parseUserId` | Token 中 `userId` claim 为空时曾可能返回 null，下游鉴权行为不确定 | **Fixed** — 空 claim 抛 `JwtException` |
| **Medium** | `JwtUtil.generateToken` | `userId == null` 时曾可生成无效 Token | **Fixed** — 抛 `IllegalArgumentException` |
| **Low** | `JwtUtil` 第 12 行 | JWT 签名密钥硬编码于源码，多环境无法轮换 | **Open** |
| **Low** | `JwtUtil` | Token 有效期固定 7 天，无刷新/吊销机制 | **Open** |
| **Info** | `GlobalExceptionHandler` | 未捕获异常统一返回 500 信封，不泄露堆栈给客户端 | **Fixed**（既有实现） |
| **Info** | `JwtUtilTest` | 补充 null userId、非法 Token 单测 | **Fixed** |

### 2.2 campus-gateway

| Severity | Location | Finding | Status |
|----------|----------|---------|--------|
| **Critical** | `AuthGlobalFilter` | 客户端可伪造 `X-User-Id` 冒充他人操作订单/商品 | **Fixed** — 所有请求先剥离该头，有效 JWT 才重新注入 |
| **High** | `AuthGlobalFilter.filter` | 同步 JWT 解析阻塞 WebFlux Netty 事件循环，高并发下存在线程饥饿风险 | **Fixed** — `Mono.fromCallable` + `Schedulers.boundedElastic()` |
| **Medium** | `AuthGlobalFilter` | CORS 预检 OPTIONS 未显式放行可能导致浏览器跨域失败 | **Fixed** — OPTIONS 直接 `chain.filter` |
| **Low** | `application.yml` | 已配置 `connect-timeout: 5s`、`response-timeout: 10s` | **Fixed** |
| **Low** | 全局 | 无请求限流 / 熔断 | **Open** |
| **Low** | 白名单 | 商品详情 GET 对未登录用户开放（设计如此） | **Open**（接受） |

### 2.3 campus-user

| Severity | Location | Finding | Status |
|----------|----------|---------|--------|
| **Medium** | `UserController` `/user/info` | 直连微服务端口时缺少网关，`X-User-Id` 可被伪造 | **Fixed**（经网关）/ **Open**（直连 8081 端口时仍无服务内校验） |
| **Medium** | `UserController` `/user/batch` | 内部 Feign 接口无 Token / 内网密钥校验，8081 映射到宿主机时可被任意调用 | **Open** |
| **Low** | `UserServiceImpl.register` | 并发注册同名用户可能竞态 | **Fixed** — `DuplicateKeyException` / `DataIntegrityViolationException` 兜底 |
| **Low** | `UserServiceImpl.login` | `user.getId()` 为空时仍可能签发 Token | **Fixed** — 抛 `INTERNAL_ERROR` |
| **Low** | `UserServiceImpl.updateUserInfo` | 空 body 仍触发无意义更新 | **Fixed** — null / 无字段时跳过 |
| **Low** | `MyMetaObjectHandler`（新增） | `createTime` / `updateTime` 未自动填充导致落库时间为 null | **Fixed** |
| **Info** | `UserServiceImpl` | 密码 BCrypt 加密存储 | **Fixed**（既有） |

### 2.4 campus-product

| Severity | Location | Finding | Status |
|----------|----------|---------|--------|
| **Critical** | `ProductMapper.deductStock` | 库存扣减非原子或缺少 `status=1 AND stock>0` 条件可能导致超卖 | **Fixed** — 单条 UPDATE 带条件，`updated==0` 区分不存在/下架/库存不足 |
| **High** | `ProductServiceImpl.restoreStock` | 取消订单重复回滚导致库存虚增 | **Fixed** — Redis `product:restore:dup:{orderNo}` 幂等键（7 天 TTL） |
| **High** | `ProductServiceImpl.restoreStock` | 已下架商品取消订单后被错误重新上架 | **Fixed** — `status==0` 时只加库存不改 status |
| **Medium** | `ProductServiceImpl.getDetail` | Redis 故障时缓存击穿打穿 DB | **Fixed** — 本地锁 + Redis 健康探测 + 5s 本地兜底缓存 |
| **Medium** | `ProductServiceImpl` | ES 写入/搜索失败导致功能不可用 | **Fixed** — ES 异常降级为 DB 模糊查询；写入失败仅 warn |
| **Medium** | `ProductController` `/product/inner/*` | 扣库存/回滚接口无鉴权，依赖网络隔离 | **Open** |
| **Medium** | `ProductController` `POST /product/reindex` | 任意已登录用户可触发全量 ES 重建（非白名单，需 Token） | **Open** |
| **Low** | `ProductServiceImpl.evictDetailCache` | 商品缓存与秒杀预热 Redis key 不同步 | **Fixed** — 同时删除 `seckill:stock:` / `seckill:product:` |
| **Low** | `MyMetaObjectHandler`（新增） | 商品 `createTime` / `updateTime` 自动填充 | **Fixed** |
| **Low** | `getDetail` 浏览量 | 缓存命中时 `viewCount` 为快照值，最长约 35 分钟滞后 | **Open**（已知取舍） |
| **Info** | ES / MySQL | 双写失败时搜索索引与 DB 短暂不一致 | **Open**（最终一致，可 `reindex` 修复） |

### 2.5 campus-order

| Severity | Location | Finding | Status |
|----------|----------|---------|--------|
| **Critical** | `OrderServiceImpl.createOrder` | 扣库存成功后落库/MQ 失败导致库存泄漏 | **Fixed** — try/catch 内调用 `productFeign.restoreStock(productId, orderNo)` |
| **Critical** | `SeckillConsumer` | DB 订单已成功但 Redis 写结果失败时被误标 `failed` 并回滚 Redis 库存 | **Fixed** — 成功/失败分支分离，DB 成功仅打 CRITICAL 日志 |
| **High** | `OrderServiceImpl.createSeckillOrder` | 秒杀落库失败未回滚 DB 库存 | **Fixed** — `deducted` 标志 + `restoreStock` 补偿 |
| **High** | `OrderServiceImpl.seckill` | MQ 发送失败时 Redis 预扣库存与占位 key 未清理 | **Fixed** — 删除 `resultKey` + `increment stockKey` |
| **Medium** | `OrderServiceImpl.pay/confirm/cancel` | 并发状态流转可能覆盖（读-改-写竞态） | **Fixed** — `lambdaUpdate` 带 `eq(status, expected)` 乐观锁 |
| **Medium** | `OrderServiceImpl.cancel` | 重复取消抛错影响幂等体验 | **Fixed** — 已取消状态直接 return |
| **Medium** | `OrderNotificationConsumer` | MQ 重复消费导致通知重复 | **Fixed** — Redis `notification:dup:{orderNo}` 去重 |
| **Medium** | `RabbitMQConfig` | 消费失败消息丢失 | **Fixed** — 订单通知/秒杀队列均配置 DLX + DLQ |
| **Low** | `application.yml` | Feign / RabbitMQ 无超时或无限重试 | **Fixed** — Feign connect 5s/read 10s；listener retry max 3 |
| **Low** | 秒杀链路 | Redis 预扣 + DB 扣减双轨，极端失败场景下两路库存可能短暂不一致 | **Open** |
| **Low** | `pay` | 平台内订单支付状态流转，无第三方支付网关 | **Resolved** — 作为订单状态接口交付 |
| **Info** | `OrderNoGenerator` | 混合时间戳 + 自增 + 随机降低碰撞；DB `order_no` 唯一约束兜底 | **Fixed**（既有 + 增强） |
| **Info** | `MyMetaObjectHandler` | 订单时间戳自动填充 | **Fixed**（order 模块既有） |

### 2.6 campus-trade-web（frontend）

| Severity | Location | Finding | Status |
|----------|----------|---------|--------|
| **Medium** | `src/api/request.js` | 401 业务码与 HTTP 401 统一清 Token 并跳登录 | **Fixed**（既有） |
| **Low** | `src/store/user.js` | Token 存 `localStorage`，存在 XSS 窃取风险 | **Open** |
| **Low** | `src/views/ProductDetail.vue` | 仅对接常规 `createOrder`，未调用 `/order/seckill` 与结果轮询 | **Open**（后端能力未暴露给用户） |
| **Low** | `src/router/index.js` | 需登录路由有 `requiresAuth` 守卫 | **Fixed**（既有） |
| **Info** | Axios | `timeout: 10000`，与网关 10s 响应超时大致对齐 | **Fixed**（既有） |

---

## 3. 已实施修复清单

以下修复均可在当前代码或 `git diff` 中核实，按模块归类。

### 3.1 campus-common — JwtUtil

- [x] 使用 `StandardCharsets.UTF_8` 构造 HMAC 密钥，避免平台默认编码差异。
- [x] `generateToken(null)` 抛 `IllegalArgumentException`。
- [x] `parseUserId` 对空 `userId` claim 抛 `JwtException`。
- [x] `JwtUtilTest` 新增 null userId 与非法 Token 用例。

### 3.2 campus-gateway — AuthGlobalFilter

- [x] 请求进入后**无条件移除**客户端 `X-User-Id`，消除越权伪造。
- [x] JWT 解析迁移至 `boundedElastic` 线程池，避免阻塞响应式事件循环。
- [x] `OPTIONS` 预检请求直接放行。
- [x] 网关 HTTP 客户端连接/响应超时配置（5s / 10s）。

### 3.3 campus-order — 订单与秒杀链路

- [x] **常规下单补偿**：`deductStock` 后落库或发 MQ 失败时，按 `orderNo` 调用 `restoreStock` 回滚。
- [x] **秒杀下单补偿**：`createSeckillOrder` 内 DB 扣库存成功后落库失败同样回滚。
- [x] **秒杀入口回滚**：MQ 发送失败时清理 `seckill:result:` 占位并 `increment seckill:stock:`。
- [x] **SeckillConsumer 逻辑修正**：DB 成功 ≠ Redis 写失败时标记 failed；仅 DB 失败才回滚 Redis 库存。
- [x] **状态流转乐观锁**：`pay` / `confirm` / `cancel` 使用条件更新防并发覆盖。
- [x] **取消幂等**：订单已取消时静默成功。
- [x] **OrderNotificationConsumer**：按 `orderNo` Redis 去重，防重复通知。
- [x] **RabbitMQ**：订单通知与秒杀队列 DLX/DLQ；消费者 retry max 3；Feign 超时配置。
- [x] **ProductFeignClient.restoreStock**：增加 `orderNo` 参数传递至商品服务幂等回滚。

### 3.4 campus-product — 库存与缓存

- [x] **原子扣库存 SQL**：`status=1 AND stock>0` 条件更新，库存为 1 时自动 `status=2`（已售）。
- [x] **幂等回滚**：`restoreStock(productId, orderNo)` 基于 Redis SETNX 防重复加库存。
- [x] **下架商品回滚**：仅加库存，不强制 `status=1`。
- [x] **Redis 降级**：健康探测 + 本地锁防击穿 + 5s 进程内兜底缓存。
- [x] **ES 降级**：搜索失败回退 DB；写入/删除失败仅告警。
- [x] **缓存失效联动**：`evictDetailCache` 同步清理秒杀预热 key。

### 3.5 campus-user / campus-product — MetaObjectHandler

- [x] **user 模块**（新增 `MyMetaObjectHandler`）：自动填充 `createTime` / `updateTime`。
- [x] **product 模块**（新增 `MyMetaObjectHandler`）：同上。
- [x] **order 模块**（既有 `MyMetaObjectHandler`）：订单时间戳自动填充。

### 3.6 campus-user — 用户服务健壮性

- [x] 注册捕获数据库唯一约束冲突，返回「用户名已存在」。
- [x] 登录校验 `userId` 非空后再签发 Token。
- [x] 更新用户信息时校验 request 非空且有实际字段变更。
- [x] `/user/batch` 解析 ids 时非法格式返回 400。

---

## 4. 仍开放的中低优先级项

| 优先级 | 项 | 影响 | 建议 |
|--------|-----|------|------|
| **Medium** | 内部接口（`/product/inner/*`、`/user/batch`）无应用层鉴权 | Docker 将 8081–8083 映射宿主机，内网未隔离时可被直接调用扣库存/查用户 | 增加内部 API Key / Spring Security 内网 IP 白名单；生产不暴露业务端口 |
| **Medium** | `POST /api/product/reindex` 任意登录用户可调用 | 恶意或误操作触发 ES 全量重建，影响搜索性能 | 限制管理员角色或移至运维脚本 |
| **Medium** | JWT 密钥硬编码 | 源码泄露即 Token 可被伪造 | 改为环境变量注入，支持轮换 |
| **Low** | 秒杀 Redis 与 DB 双轨库存 | 消费者异常、人工删 key 等极端场景可能短暂不一致 | 增加对账任务或以 DB 为唯一真相源收敛 Redis |
| **Low** | ES 与 MySQL 最终一致 | 搜索列表可能短暂滞后 | 定期 reindex 或引入 Canal 同步 |
| **Low** | 网关无限流 | 易被刷接口拖垮下游 | 接入 Sentinel / Redis 限流 |
| **Low** | Token 存 localStorage | XSS 可窃取会话 | 改 HttpOnly Cookie 或缩短有效期 + Refresh Token |
| **Low** | 前端未对接秒杀 API | 秒杀能力仅 API 层可用 | 补充 UI 与轮询 `/order/seckill/result/{productId}` |
| **Low** | 商品详情缓存中浏览量滞后 | 展示值略低于真实值 | 可接受；或单独 counter 不走详情缓存 |
| **Low** | 平台内支付状态流转 | 无第三方资金流 | 按订单状态接口交付 |
| **Info** | docker-compose 默认弱密码（MySQL/RabbitMQ） | 本地演示风险低，公网部署有风险 | 生产改用 secrets 管理 |

---

## 5. 验证步骤

### 5.1 构建验证

在仓库根目录执行：

```bash
cd campus-trade
mvn clean package -DskipTests
```

**预期结果**：

- 六个子模块（`campus-common`、`campus-gateway`、`campus-user`、`campus-product`、`campus-order` 及父 POM）均 `BUILD SUCCESS`。
- 各服务 `target/` 下生成可执行 JAR。

可选：运行 common 模块单测：

```bash
cd campus-trade/campus-common
mvn test -Dtest=JwtUtilTest
```

### 5.2 容器冒烟验证

在仓库根目录执行：

```bash
docker-compose up --build -d
docker-compose ps
```

等待全部服务 `healthy` 后，按下列清单逐项验证。

#### 5.2.1 基础设施

| # | 检查项 | 命令 / 操作 | 预期 |
|---|--------|-------------|------|
| 1 | Nacos | 浏览器打开 `http://localhost:8848/nacos` | 可访问，服务列表可见 4 个微服务 |
| 2 | MySQL | `docker exec campus-mysql mysqladmin ping -uroot -proot123` | `mysqld is alive` |
| 3 | Redis | `docker exec campus-redis redis-cli ping` | `PONG` |
| 4 | RabbitMQ | 浏览器打开 `http://localhost:15672`（guest/guest） | 管理台可登录，存在 order/seckill 相关队列 |
| 5 | Elasticsearch | `curl -s http://localhost:9200/_cluster/health` | `status` 为 `green` 或 `yellow` |

#### 5.2.2 网关与鉴权

| # | 检查项 | 命令 / 操作 | 预期 |
|---|--------|-------------|------|
| 6 | 白名单放行 | `curl -s http://localhost:8080/api/product/list` | HTTP 200，`code:200` |
| 7 | 未登录拦截 | `curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/order/buyer` | `401` |
| 8 | 伪造 X-User-Id 无效 | 带 `X-User-Id: 999` 但不带 Token 访问 `/api/order/buyer` | 仍返回 401（头被剥离） |
| 9 | 正常登录 | `POST /api/user/register` → `POST /api/user/login` 取 Token | 返回 `token` 与 `userId` |

#### 5.2.3 核心业务链路

| # | 检查项 | 操作 | 预期 |
|---|--------|------|------|
| 10 | 发布商品 | 登录后 `POST /api/product` | 返回 `productId` |
| 11 | 商品详情 | `GET /api/product/{id}` | 返回详情，`viewCount` 递增 |
| 12 | 常规下单 | 买家 Token + `POST /api/order` `{productId}` | 下单成功，库存减 1 |
| 13 | 取消回滚 | `POST /api/order/{id}/cancel` | 订单变已取消，库存恢复 |
| 14 | 不能买自己的 | 卖家 Token 购买自己的商品 | 业务错误码，提示不能购买自己的商品 |
| 15 | 支付/确认 | `POST .../pay` → `POST .../confirm` | 状态 0→1→2 |
| 16 | 卖家通知 | 下单后查 `GET /api/notification/list`（卖家 Token） | 有新 ORDER_CREATED 通知 |
| 17 | 搜索 | `GET /api/product/search?keyword=xxx` | 返回结果（ES 或 DB 降级） |

#### 5.2.4 前端冒烟

| # | 检查项 | 操作 | 预期 |
|---|--------|------|------|
| 18 | 前端访问 | 浏览器打开 `http://localhost` | 首页加载正常 |
| 19 | 登录流程 | 注册 → 登录 → 发布 → 购买 → 我的订单 | 全流程无 401 卡死，401 时自动跳登录页 |
| 20 | 商品详情购买 | 详情页点击「立即购买」 | 跳转订单页，后端订单可见 |

#### 5.2.5 补偿与异常（可选深入）

| # | 检查项 | 说明 | 预期 |
|---|--------|------|------|
| 21 | 库存幂等回滚 | 同一 `orderNo` 重复调用 restore | 第二次直接返回，库存不重复增加 |
| 22 | 秒杀 API | `POST /api/order/seckill` + 轮询 `GET /api/order/seckill/result/{productId}` | 返回 queueId，最终 status=1 且含 orderNo（前端 UI 未集成，需 curl/Postman） |
| 23 | 日志关键字 | `docker-compose logs campus-order \| grep CRITICAL` | 正常运行无 CRITICAL；注入故障测试时可观测补偿日志 |

### 5.3 清理

```bash
docker-compose down
# 如需清空数据卷：
# docker-compose down -v
```

---

## 附录：关键文件索引

| 文件 | 职责 |
|------|------|
| `campus-trade/campus-common/src/main/java/com/campus/common/util/JwtUtil.java` | JWT 签发与解析 |
| `campus-trade/campus-gateway/src/main/java/com/campus/gateway/filter/AuthGlobalFilter.java` | 网关鉴权 |
| `campus-trade/campus-order/src/main/java/com/campus/order/service/impl/OrderServiceImpl.java` | 下单/秒杀/补偿 |
| `campus-trade/campus-order/src/main/java/com/campus/order/listener/SeckillConsumer.java` | 秒杀异步消费 |
| `campus-trade/campus-product/src/main/java/com/campus/product/service/impl/ProductServiceImpl.java` | 库存/缓存/ES |
| `campus-trade/campus-product/src/main/java/com/campus/product/mapper/ProductMapper.java` | 原子库存 SQL |
| `campus-trade/campus-*/src/main/java/**/MyMetaObjectHandler.java` | 时间戳自动填充 |
| `campus-trade-web/src/api/request.js` | 前端 Token 与 401 处理 |
| `docker-compose.yml` | 全栈编排 |

---

*本报告基于代码静态审查生成，随仓库变更需重新评估。*
