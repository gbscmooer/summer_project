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

## 3. 阶段 A：3 台验证（冒烟）

| 主机 | 角色 | 启动 |
|------|------|------|
| `campus-mw` | 中间件 | `docker compose -f docker-compose.mw.yml --env-file .env up -d` |
| `campus-edge` | Edge + user | `docker compose -f docker-compose.edge.yml -f docker-compose.user.yml --env-file .env up -d` |
| `campus-app` | product + order | `docker compose -f docker-compose.product.yml -f docker-compose.order.yml --env-file .env up -d` |

---

## 4. 阶段 B：8 台集群

### 4.1 部署顺序

```text
① Data-Main → Data-Sub（主从复制）
② Middleware（Nacos / ES / MQ）
③ App-A → App-B → App-C（注册进 Nacos）
④ 内网 CLB 后端：App-A:8080、App-B:8080
⑤ Edge-A / Edge-B + 公网 CLB + DNS
⑥ 端到端：注册 / 登录 / 发商品 / 下单 / 搜索
```

### 4.2 CLB 健康检查（TCP）

| CLB | 后端 | 检查方式 |
|-----|------|----------|
| **公网 CLB** | Edge-A/B `:443` | TCP 443 |
| **内网 CLB** | App-A/B `:8080` | **TCP 8080** |

> 本项目 Gateway **无** `/actuator/health` 端点；若 CLB 配成 HTTP 健康检查会误摘流。  
> Nacos 控制台可用 `http://172.17.178.193:8848/nacos/` 人工验收。

### 4.3 应用连接串

业务读写一律指向 **Data-Main**：

```bash
MYSQL_HOST=172.17.178.195
REDIS_HOST=172.17.178.195
NACOS_HOST=172.17.178.193
```

Edge 节点额外设置：

```bash
GATEWAY_UPSTREAM=http://172.17.178.198:8080
PUBLIC_BASE_URL=https://summer.huangzixuan.asia
CORS_ALLOWED_ORIGINS=https://summer.huangzixuan.asia
```

### 4.4 各机 `.env` 准备

```bash
cd ops/multi-node
cp .env.cluster.example .env
# 改 HOST_IP 为本机私网；填写 Secret（勿提交 Git）
```

---

## 5. 一次性准备（所有机器）

1. Alibaba Cloud Linux 3 / Ubuntu 22.04 + Docker + Compose 插件  
2. 仓库同步到各 ECS  
3. **构建机**打包：

```bash
cd campus-trade && mvn -q package -DskipTests
cd ../campus-trade-web && npm ci && npm run build
docker compose -f ops/multi-node/docker-compose.edge.yml build
docker compose -f ops/multi-node/docker-compose.user.yml build
docker compose -f ops/multi-node/docker-compose.product.yml build
docker compose -f ops/multi-node/docker-compose.order.yml build
```

4. 各机 `HOST_IP` = 本机私网 IP（见 §1 表）  
5. `/etc/hosts` 或 PrivateZone，见 `hosts.example`

---

## 6. 安全组（同 VPC 172.17.176.0/20）

| 方向 | 端口 | 放行 |
|------|------|------|
| 公网 → Edge | 80 / 443 | 仅 Edge-A/B |
| VPC → MW | 8848,9848,9200,5672 | App / Edge |
| VPC → Data | 3306,6379 | App / MW |
| VPC → App Gateway | 8080 | 内网 CLB、Edge |
| 禁止 | 8081～8083、3306、6379 对公网 | — |

---

## 7. 验收

### Nacos 实例

`http://172.17.178.193:8848/nacos` — 应见 gateway / user / product / order，实例 IP 为各 ECS 私网地址。

```bash
./scripts/check-nacos.sh
./scripts/health-check.sh   # 不输出 Secret
```

### 浏览器

`https://summer.huangzixuan.asia`（经公网 CLB 47.86.103.153）

---

## 8. 高可用演练

| 演练 | 操作 | 预期 |
|------|------|------|
| Gateway 单点 | 停 App-A Gateway | 内网 CLB TCP 摘除；站点仍可用 |
| Product 单点 | stop 一台 product | Nacos 摘除；详情仍可用 |
| App-C 整机 | 停 172.17.178.192 | User/Order 仍有另一实例 |
| MW 整机 | 停 Middleware | **全站不可用**（单点边界） |

```bash
./scripts/chaos-stop.sh product
./scripts/chaos-start.sh product
```

---

## 9. 图片多实例

Product 多机时本地盘不共享。答辩推荐：**仅 App-A product 接上传**，列表/详情走双实例。

---

## 10. 回滚 / 省钱

```bash
docker compose -f <角色yml> --env-file .env down
# 答辩结束：释放按量 ECS；保留镜像
```

---

## 11. 明确不做（本阶段）

- Nacos 三节点 Raft 集群  
- 应用侧 MySQL 读写分离 / Redis Sentinel 绑定  
- Spring Actuator 健康端点  
- K8s  

列为后续演进即可。
