# 多机部署动作手册（答辩 / 运维）

面向：**3 台验证** → **8 台集群答辩落地**。
**不改业务 Java/Vue 逻辑**；靠 Compose、环境变量、Nacos 注册 IP、CLB、安全组完成。

公网入口：**47.86.103.153**（公网 CLB）→ 域名 **summer.huangzixuan.asia**
VPC 网段：**172.17.176.0/20**

---

## 0. 结论：要不要改代码？

| 类型 | 要不要改 |
|------|----------|
| 业务代码（Controller/Service） | **不用** |
| 新增 K8s | **不用** |
| 本目录 Compose / `.env` / 脚本 | **要加（运维交付物）** |
| 各机 `/etc/hosts` 或 PrivateZone | **要配** |
| Nacos 注册 IP（`SPRING_CLOUD_NACOS_DISCOVERY_IP`） | **必须配**（否则注册成 Docker 桥接 IP） |

> **说明**：当前 Spring Boot 服务**未引入 Actuator**，CLB 健康检查请使用 **TCP 端口探测**（见 §4），勿配置 HTTP `/actuator/health`。

---

## 1. 八台节点映射

| 角色 | 私网 IP | 公网 IP | 说明 |
|------|---------|---------|------|
| Edge-A | 172.17.178.190 | 8.218.58.199 | Caddy + campus-web |
| Edge-B | 172.17.178.191 | 47.243.210.172 | Caddy + campus-web |
| App-A | 172.17.178.189 | 47.76.254.89 | Gateway + User + Product |
| App-B | 172.17.178.197 | 8.218.45.65 | Gateway + Order + Product |
| App-C | 172.17.178.192 | 8.210.53.78 | User + Order |
| Middleware | 172.17.178.193 | 47.242.124.102 | Nacos / ES / RabbitMQ |
| Data-Sub | 172.17.178.194 | 8.218.2.200 | MySQL 从 + Redis 从 |
| Data-Main | 172.17.178.195 | 47.242.51.82 | MySQL 主 + Redis 主 |
| 内网 CLB | **172.17.178.198** | — | Gateway 后端池 VIP |

hosts 示例见 `hosts.example`；环境变量模板见 `.env.cluster.example`。

---

## 2. 架构概览

```text
用户 → 公网 CLB (47.86.103.153) → Edge-A/B (:443)
              │
              │  /api/* → 内网 CLB VIP
              ▼
         内网 CLB (172.17.178.198:8080)
              │
              ├─► App-A Gateway :8080
              └─► App-B Gateway :8080
                        │
         ┌──────────────┼──────────────┐
         ▼              ▼              ▼
      App-C         Middleware      Data-Main / Data-Sub
   User+Order    Nacos/ES/MQ      MySQL + Redis
```

---

## 3. 干净机器完整流程（从仓库根目录执行）

以下命令均在 **仓库根目录** `/path/to/summer_project` 执行。

### 3.1 系统准备

1. Ubuntu 22.04 / Alibaba Cloud Linux 3 + Docker + Compose 插件
2. 将仓库同步到各 ECS（同一 Git commit）
3. 安全组、CLB、PrivateZone 按 §6 配置

### 3.2 构建（每 Edge/App 节点本地构建）

> **镜像策略**：答辩方案在 **每个 Edge/App 节点** 从同一 Git commit **本地 build**；Middleware/Data 使用 **官方镜像**；**不会**自动分发业务镜像，需各节点自行构建或自行推送到 ACR 后拉取。

```bash
(cd campus-trade && mvn -q package -DskipTests)
(cd campus-trade-web && npm ci && npm run build)

docker compose -f ops/multi-node/docker-compose.edge.yml build
docker compose -f ops/multi-node/docker-compose.app-a.yml build
docker compose -f ops/multi-node/docker-compose.app-b.yml build
docker compose -f ops/multi-node/docker-compose.app-c.yml build
```

### 3.3 各机 `.env`

```bash
cd ops/multi-node
cp .env.cluster.example .env
# 改 HOST_IP 为本机私网 IP；填写 Secret（勿提交 Git）
```

统一连接地址（`.env.cluster.example` 已给出）：

```bash
MW_HOST=172.17.178.193
NACOS_HOST=172.17.178.193
ES_HOST=172.17.178.193
RABBITMQ_HOST=172.17.178.193
MYSQL_HOST=172.17.178.195
MYSQL_PRIMARY_HOST=172.17.178.195
REDIS_HOST=172.17.178.195
REDIS_PRIMARY_HOST=172.17.178.195
GATEWAY_UPSTREAM=http://172.17.178.198:8080
```

Edge 额外注意：

```bash
WEB_BIND_ADDRESS=127.0.0.1
WEB_PORT=8080
TRUSTED_PROXY_CIDR=<公网SLB回源网段>
```

**campus-web 只绑定 `127.0.0.1:8080`**，公网由同机 Caddy `:80/:443` 反代。

### 3.4 启动顺序与命令

```text
① Data-Main → prepare-primary-repl.sh
② Data-Sub  → setup-replication.sh
③ Middleware
④ App-A → App-B → App-C
⑤ 内网 CLB 后端：App-A:8080、App-B:8080（TCP 健康检查）
⑥ Edge-A / Edge-B + 公网 CLB + DNS
⑦ 端到端验收
```

各节点在 `ops/multi-node` 目录执行：

| 节点 | 命令 |
|------|------|
| Data-Main | `./scripts/up-role.sh data-primary` → `./scripts/prepare-primary-repl.sh` |
| Data-Sub | `./scripts/up-role.sh data-replica` → `./scripts/setup-replication.sh` |
| Middleware | `./scripts/up-role.sh mw` |
| App-A | `./scripts/up-role.sh app-a` |
| App-B | `./scripts/up-role.sh app-b` |
| App-C | `./scripts/up-role.sh app-c` |
| Edge-A/B | `./scripts/up-role.sh edge` |

`setup-replication.sh` 可安全重跑：复制已健康时跳过 RESET；有业务表但未复制时需 `FORCE_REPLICA_RESET=1`。

### 3.5 公网 Caddy 证书初始化（双 Edge）

1. 公网 CLB 的 HTTP/HTTPS **先只回源 Edge-A**
2. Edge-A 上 Caddy 完成 Let's Encrypt 申请
3. 将 HTTP 临时切到 **Edge-B**，Edge-B 获取证书
4. 最后将 **Edge-A 与 Edge-B** 都加入公网 CLB 服务器组

DNS 须指向公网 CLB；安全组放行 80/443。

---

## 4. CLB 健康检查（TCP）

| CLB | 后端 | 检查方式 |
|-----|------|----------|
| **公网 CLB** | Edge-A/B `:443` | TCP 443 |
| **内网 CLB** | App-A/B `:8080` | **TCP 8080** |

> Gateway **无** Actuator；HTTP `/actuator/health` 不可用。
> Nacos 控制台：`http://172.17.178.193:8848/nacos/`

---

## 5. 阶段 A：3 台验证（冒烟）

| 主机 | 角色 | 启动 |
|------|------|------|
| `campus-mw` | 中间件 | `docker compose -f docker-compose.mw.yml --env-file .env up -d` |
| `campus-edge` | Edge + user | `docker compose -f docker-compose.edge.yml -f docker-compose.user.yml --env-file .env up -d` |
| `campus-app` | product + order | `docker compose -f docker-compose.product.yml -f docker-compose.order.yml --env-file .env up -d` |

---

## 6. 安全组（同 VPC 172.17.176.0/20）

| 方向 | 端口 | 放行 |
|------|------|------|
| 公网 → Edge | 80 / 443 | 仅 Edge-A/B |
| VPC → MW | 8848,9848,9200,5672 | App / Edge |
| VPC → Data | 3306,6379 | App / MW |
| VPC → App Gateway | 8080 | 内网 CLB、Edge |
| VPC → App 服务 | **8081,8082,8083** | VPC 内互通（排障/Nacos 直连） |
| 禁止 | 8081～8083、3306、6379 对公网 | — |

---

## 7. Redis Sentinel 说明（答辩口径）

- 当前仅 **Sentinel-1（Data-Main）** 与 **Sentinel-2（Data-Sub）**，**无第三个 Sentinel**
- Sentinel **仅用于状态监控与答辩展示**
- **应用未接入 Sentinel**，业务仍直连 `REDIS_HOST`（Data-Main Master）
- Redis 主故障后需 **人工修改各 App `REDIS_HOST` 并滚动重启**
- **不宣称** Redis 自动业务故障转移

---

## 8. 验收

```bash
cd ops/multi-node
./scripts/check-nacos.sh
./scripts/health-check.sh   # 按本机角色检查；无角色容器则失败
```

浏览器：`https://summer.huangzixuan.asia`

---

## 9. 高可用演练（已真机验证）

| 演练 | 操作 | 预期 |
|------|------|------|
| Gateway 单点 | 停 App-A Gateway | 内网 CLB TCP 摘除；站点仍可用 |
| Product 单点 | stop 一台 product | Nacos 摘除；详情仍可用 |
| Edge 单点 | 停一台 Edge | 公网 CLB 摘除；站点仍可用 |
| App-C 整机 | 停 172.17.178.192 | User/Order 仍有另一实例 |
| MW 整机 | 停 Middleware | **全站不可用**（单点边界） |

```bash
./scripts/chaos-stop.sh product
./scripts/chaos-start.sh product
```

MySQL 提升：`./scripts/promote-mysql.sh`（默认检查复制滞后；`FORCE_PROMOTE=1` 强制）。

---

## 10. 图片多实例

Product 多机时本地盘不共享。答辩推荐：**仅 App-A product 接上传**，列表/详情走双实例。

---

## 11. 回滚 / 省钱

```bash
docker compose -f <角色yml> --env-file .env down
```

---

## 12. 明确不做（本阶段）

- Nacos 三节点 Raft 集群
- 应用侧 MySQL 读写分离
- 应用绑定 Redis Sentinel 自动切换
- Spring Actuator 健康端点
- K8s

列为后续演进即可。
