# 多机部署动作手册（答辩 / 运维）

面向：先 **3 台验证**，再按量扩到 **10 台业务 + 1 台压测**。  
**不改业务 Java/Vue 逻辑**；靠 Compose、环境变量、Nacos 注册 IP、安全组完成。

---

## 0. 结论：要不要改代码？

| 类型 | 要不要改 |
|------|----------|
| 业务代码（Controller/Service） | **不用** |
| 新增 K8s | **不用** |
| 本目录 Compose / `.env` / 脚本 | **要加（运维交付物）** |
| 各机 `/etc/hosts` 或 PrivateZone | **要配** |
| Nacos 注册 IP（`SPRING_CLOUD_NACOS_DISCOVERY_IP`） | **必须配**（否则注册成 Docker 桥接 IP） |

---

## 1. 角色与 3→10 映射

### 阶段 A：3 台验证

| 主机名建议 | 角色 | 启动命令 |
|------------|------|----------|
| `campus-mw` | 中间件 | `docker compose -f docker-compose.mw.yml --env-file .env up -d` |
| `campus-edge` | Nginx + Gateway + user | `docker compose -f docker-compose.edge.yml -f docker-compose.user.yml --env-file .env up -d` |
| `campus-app` | product + order | `docker compose -f docker-compose.product.yml -f docker-compose.order.yml --env-file .env up -d` |

### 阶段 B：10 台业务（答辩临时）

| # | 角色 | Compose |
|---|------|---------|
| 1 | MW | `docker-compose.mw.yml` |
| 2 | EDGE-A（Nginx+Gateway） | `docker-compose.edge.yml` |
| 3 | EDGE-B（可选热备） | 同上 |
| 4～5 | user ×2 | `docker-compose.user.yml` |
| 6～8 | product ×3 | `docker-compose.product.yml` |
| 9～10 | order ×2 | `docker-compose.order.yml` |
| 11 | 压测机 | 只装 JMeter，不跑业务 |

---

## 2. 一次性准备（所有机器）

1. 系统：Alibaba Cloud Linux 3 或 Ubuntu 22.04 + Docker + Compose 插件  
2. 把本仓库拷到各机（或自定义镜像里打好）  
3. 在 **一台构建机** 上：

```bash
cd campus-trade && mvn -q package -DskipTests
cd ../campus-trade-web && npm ci && npm run build
```

4. 构建并保存镜像（或推到阿里云 ACR）：

```bash
docker compose -f ops/multi-node/docker-compose.edge.yml build
docker compose -f ops/multi-node/docker-compose.user.yml build
docker compose -f ops/multi-node/docker-compose.product.yml build
docker compose -f ops/multi-node/docker-compose.order.yml build
# mw 用官方镜像，无需 build
```

5. 复制 `ops/multi-node/.env.example` → 各机 `.env`，填 Secret；**按角色删掉不需要的变量**（见文件内注释）。  
6. 每台设置本机私网 IP：

```bash
export HOST_IP=$(hostname -I | awk '{print $1}')
# 写入 .env：HOST_IP=10.0.1.x
```

7. 配置 hosts（或云解析 PrivateZone），见 `hosts.example`。

---

## 3. 安全组（同 VPC）

| 方向 | 端口 | 放行 |
|------|------|------|
| 公网 → EDGE | 80/443 或 8080 | 仅入口 |
| APP/EDGE → MW | 3306,6379,9200,5672,8848,9848 | 仅 VPC 网段 |
| 压测 → EDGE | 8080/443 | 压测机 IP |
| 禁止 | 8081～8083 对公网 | — |

---

## 4. 启动顺序（动作清单）

```text
① MW 健康
② EDGE（Gateway 能连上 Nacos）
③ user / product / order（Nacos 出现多 IP 实例）
④ 浏览器打开 EDGE:8080 走通注册登录
⑤ 压测机开打
```

### 4.1 启动 MW

```bash
cd /path/to/repo/ops/multi-node
cp .env.example .env   # 首次
# 编辑 MW_HOST、密码等
docker compose -f docker-compose.mw.yml --env-file .env up -d
docker compose -f docker-compose.mw.yml ps
```

### 4.2 启动 EDGE

```bash
# .env 中 MW_HOST=10.0.1.11  HOST_IP=本机私网IP
docker compose -f docker-compose.edge.yml --env-file .env up -d
# 3 台验证阶段可同机再起 user：
docker compose -f docker-compose.user.yml --env-file .env up -d
```

### 4.3 启动 APP

```bash
docker compose -f docker-compose.product.yml --env-file .env up -d
docker compose -f docker-compose.order.yml --env-file .env up -d
```

### 4.4 验收 Nacos

浏览器：`http://MW_HOST:8848/nacos`（若未暴露公网，用 SSH 隧道）  
应看到 `campus-gateway/user/product/order`，实例 IP = 各 ECS 私网 IP。

```bash
./scripts/check-nacos.sh
```

---

## 5. 扩到 10 台（只加副本）

对新 ECS（已导入镜像 + `.env` 中 `HOST_IP` 改成本机）：

```bash
# 例：第 2、3 台 product
docker compose -f docker-compose.product.yml --env-file .env up -d
```

Nacos 中 `campus-product` 出现多个不同 IP 即负载均衡生效。  
Gateway / Feign **不用改代码**，已是 `lb://`。

---

## 6. 高可用 / 容灾演练动作

```bash
# 杀掉本机 product（模拟实例故障）
./scripts/chaos-stop.sh product

# 随机停一个业务容器（排除中间件）
./scripts/chaos-random-app.sh

# 恢复
./scripts/chaos-start.sh product
```

| 演练 | 操作 | 预期 |
|------|------|------|
| 实例故障 | stop 一台 product | Nacos 摘除；详情仍可用 |
| 滚动更新 | 逐台 `compose up -d --build` | 不全站中断 |
| 整机故障 | 停掉一台 APP ECS | 其余实例承接 |
| MW 故障 | 停 MW | 全站不可用（演示单点，勿当成功容灾） |

---

## 7. 压测 / 类 DDoS

- 高并发：打详情 / 秒杀（见仓库根目录 `campus_trade_performance.jmx`）  
- 防刷：打登录 → 预期大量 **429**（单压测 IP 会触顶，属正常）  
- 多源 CC：2～3 台压测机不同 IP 同时打  

将压测机 IP 写入 EDGE 的 Nginx 豁免：改 `campus-trade-web/nginx.conf` 里 `geo` 段中的 IP，或重建 web 镜像前替换为 `BENCH_IP`。

---

## 8. 图片多实例说明

`product` 多机时本地磁盘不共享。答辩可选：

1. 只用 **一台** product 接上传，其余只读流量；或  
2. 多台挂载同一 NFS 到 `/data/product-images`。

---

## 9. 回滚 / 省钱

```bash
docker compose -f <角色yml> --env-file .env down
# 答辩结束：释放按量 ECS；保留自定义镜像
```
