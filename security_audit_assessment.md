# 上云前安全审计报告

审计日期：2026-07-11
范围：Java 微服务、Vue 前端、数据库脚本、依赖、Nginx、Docker Compose 与容器构建

## 结论

本轮确认的 Critical / High 代码与默认部署漏洞均已修复。修改后测试、构建和依赖复扫未发现仍未处理的 Critical / High 项。项目可以进入受控云部署，但必须先完成文末的部署前动作；旧运行容器不会自动获得仓库中的修复。

## 已修复的确定漏洞

| 原等级 | 问题 | 修复 |
| --- | --- | --- |
| Critical | JWT 使用仓库内固定 HMAC 密钥，可伪造任意用户 | 改为必填 `JWT_SECRET`，少于 32 字节拒绝启动；增加 issuer、audience、jti，并将默认有效期缩短为 120 分钟 |
| Critical | 8081～8083 直连后可伪造 `X-User-Id` 绕过网关 | 生产 Compose 不再发布网关、业务服务和中间件端口；仅 Web 回环端口可达，并拆分 edge/service/data/egress 网络 |
| Critical | `/api/product/inner/**`、`/api/user/batch` 可经公网网关调用 | 网关明确返回 404；内部接口强制校验 32 字节以上 `X-Internal-Token`，Feign 自动注入 |
| Critical | Redis 公网无密码且允许任意 Jackson 多态类型，存在缓存投毒/RCE 风险 | Redis 仅 data 网络可达并启用强制密码；反序列化改为业务 DTO 包白名单，恶意 `@class` 测试已覆盖 |
| Critical | 管理员可把 AI Base URL 改为攻击地址并沿用旧 Key，造成 Key 外传及 SSRF | 只允许解析到公网地址的 HTTPS URL；拒绝回环/私网/链路本地地址；禁用重定向；切换地址必须同时提交新 Key |
| Critical | AI Key 明文存数据库 | 使用部署必填的 `AI_CONFIG_ENCRYPTION_KEY` 做 AES-GCM 加密；启动时主动迁移旧明文且旧值必须重新绑定 endpoint 后才能使用；列扩至 1024 字节 |
| High | MySQL、Redis、RabbitMQ 使用公开默认凭据并暴露宿主端口 | 生产 Compose 改为无默认值的必填随机 Secret，取消宿主端口发布；开发中间件端口仅绑定 `127.0.0.1` |
| High | 任意登录用户可触发 ES 全量重建 | `POST /api/product/reindex` 增加管理员校验 |
| High | 用户批量内部接口可枚举手机号 | 接口仅返回 ID 与昵称，限制单次 100 个，并要求内部令牌 |
| High | 秒杀消息重复投递可重复扣库存/建单 | 消息加入 `requestId`，订单表增加唯一幂等键，重复消费返回既有订单 |
| High | 库存恢复只依赖 Redis 临时幂等键，Redis 故障可重复加库存 | 新增数据库持久幂等流水，以 `(product_id, order_no)` 唯一约束；订单号改为必填 |
| High | 跨服务库存补偿失败只写日志，可永久丢失库存 | 32 位 UUID 订单号绑定扣减幂等流水；订单侧独立事务记录补偿意图并在业务事务持行锁，重试器用 `SKIP LOCKED` 跳过进行中订单，失败或崩溃后持续重试 |
| High | AI/图片并发可耗尽 256MiB 堆或无限消耗付费额度 | AI 单实例完整流程限 4 并发、Redis 跨实例限 8、图片解码限 2 并发/1000 万像素、每用户每日默认 100 次、模型超时封顶 85 秒 |
| High | 注册用户可反复上传图片填满卷 | Nginx 上传限流；每用户最多 50 张；保留 1GiB 最低空闲水位；失败自动清理已写文件 |
| Medium | 图片只校验 MIME 与魔数，可上传截断/伪造图片 | 使用 ImageIO/TwelveMonkeys 完整解码 JPG/PNG/WEBP，限制 8192 边长和 2500 万像素 |
| Medium | 任意 HTTPS Origin CORS + credentials | 改为精确 `CORS_ALLOWED_ORIGINS`，Bearer 模式关闭 credentials |
| Medium | Nginx 缺少安全响应头与敏感接口限流 | 增加 CSP、nosniff、frame deny、HSTS、Referrer/Permissions Policy，以及登录、注册、AI、上传限流；只从必填可信代理 CIDR 解析真实客户端 IP |
| Medium | 容器以 root 运行、日志无限增长 | Java 与 Web 镜像改为非 root、只读根文件系统、drop capabilities、no-new-privileges；日志轮转且生产日志默认 INFO |

## 依赖结果

- 前端：Vite 升级到 6.4.3；`npm audit` 与 `npm audit --omit=dev` 均为 0。
- 后端：Spring Boot 3.5.16、Spring Cloud 2025.0.3、Spring Cloud Alibaba 2025.0.0.0、MyBatis-Plus 3.5.17、JJWT 0.13.0；MySQL 驱动由 Boot 管理为 9.7.0。
- CycloneDX 聚合 SBOM 共 173 个组件。OSV 复扫为 0 Critical、0 High、1 Medium。唯一条目 `GHSA-5jmj-h7xm-6q6v` 标注从 Jackson 3.1.0 引入，而实际组件为 2.21.4，且项目未启用该公告所需的大小写不敏感属性配置；判定为版本范围误报，无可用修复版本。

## 验证结果

- `mvn clean package`：成功，41 个后端测试通过，4 个服务 JAR 构建成功。
- `npm test`：成功。
- `npm run build`：成功。
- `npm audit`：0 漏洞。
- `docker compose config --quiet`：生产与本地中间件配置均通过。
- Nginx `nginx -t`：通过。
- Docker 应用镜像构建：5 个应用镜像均成功；Java 服务运行 UID/GID 均为 `10001:10001`，Web 运行 UID/GID 为 `101:101`；图片卷初始化器已用临时卷验证。
- `git diff --check`：通过。

## 部署前必须完成

1. 立即在 AI 服务供应商后台吊销并轮换审计前本机 `.env` 中的 Key。该 Key 曾进入工具输出，不能继续使用。
2. 用独立随机值填写 `.env.example` 列出的全部必填 Secret；不得复用 JWT、内部令牌、数据库密码和 AI 加密主密钥。将 `TRUSTED_PROXY_CIDR` 精确设置为 HTTPS 代理/LB 的源网段，禁止使用 `0.0.0.0/0`。
3. 现有数据库卷先备份，再对照 `sql/init.sql` 确认订单 request ID、扣减/恢复流水、补偿任务表和 AI Key endpoint 绑定列已存在（缺则补齐或重建卷）。启动后确认旧 AI Key 已为 `enc:v1:` 密文，并在管理员页重新提交轮换后的 Key（不得打印 Key）。
4. 同机 HTTPS 代理接入默认 `127.0.0.1:8080`；外部 LB 则将 `WEB_BIND_ADDRESS` 设为实例私网 IP，并用安全组限定 Web 端口仅允许 LB 源 CIDR。公网安全组仅开放 80/443。
5. 禁止生产执行 `sql/seed-dev.sql`；管理员必须通过受控离线流程授权，不存在默认管理员密码。
6. 重新构建并重启 Compose；当前正在运行的旧容器仍使用审计前配置且仍暴露多个端口。

## 非阻断残余风险

- 浏览器 Token 仍存于 `localStorage`。当前未发现 XSS sink，且 CSP 与 120 分钟 Token 有效期降低了风险；后续可迁移至 HttpOnly/Secure/SameSite Cookie 并增加 CSRF 防护与会话撤销。
- Compose 内的 Elasticsearch 关闭自身 TLS/认证，但只连接隔离的内部 data 网络；若迁移到共享网络或托管集群，必须启用服务端认证与 TLS。
- 本轮为代码、依赖、配置和构建审计，不替代对实际云域名、WAF、安全组、证书、KMS、备份恢复和外部渗透测试的验收。
