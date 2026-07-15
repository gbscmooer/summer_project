# 启动与安装

多机部署（3 台验证 → 扩到 10 台）、故障演练与 Nacos 跨机注册说明见 [`ops/multi-node/README.md`](ops/multi-node/README.md)。

## 环境

- JDK 17
- Maven 3.9+
- Node.js 18+
- Docker Desktop 或兼容 Docker Compose 的运行环境
- 可访问的 OpenAI-compatible 多模态模型 API 与 API Key

## 安装依赖

```bash
cd campus-trade
mvn test
mvn package -DskipTests

cd ../campus-trade-web
npm install
npm test
npm run build
```

## Docker 启动

复制环境变量模板，并为每个必填 Secret 生成独立随机值（不要复用）：

```bash
cp .env.example .env
openssl rand -base64 48
```

将生成值分别写入 `.env` 的 `MYSQL_ROOT_PASSWORD`、`MYSQL_PASSWORD`、`REDIS_PASSWORD`、`RABBITMQ_PASSWORD`、`JWT_SECRET`、`INTERNAL_API_TOKEN` 和 `AI_CONFIG_ENCRYPTION_KEY`。`RABBITMQ_USERNAME` 也必须设置。AI Key 仅写入部署 Secret；如曾使用审计前的本地 `.env` Key，部署前必须先在供应商后台吊销并轮换。

Compose 默认只在 `127.0.0.1:8080` 暴露 Web，适用于同机 HTTPS 反向代理。将 `TRUSTED_PROXY_CIDR` 精确设置为该代理连接 Web 容器时使用的源网段（通常是 Docker bridge 网段），禁止填写 `0.0.0.0/0`。

若使用实例外部的 HTTPS Load Balancer，将 `WEB_BIND_ADDRESS` 设置为实例私网 IP，将 `TRUSTED_PROXY_CIDR` 设置为 LB 的实际源 CIDR，并用安全组限制 Web 端口只能由 LB 访问。公网只开放 80/443；不要公开 8081～8083、3306、6379、8848、9200、9300、5672 或 15672。

数据库 schema 以 `sql/init.sql` 为唯一权威（Compose 首次初始化自动挂载）。已有旧数据卷若缺列/缺表，建议备份后重建卷，或对照 `init.sql` 手工补齐。管理员角色须通过受控离线数据库操作授予，仓库不提供默认管理员。

已有库若缺少用户细粒度权限列，可执行：

```sql
ALTER TABLE t_user
  ADD COLUMN perm_post TINYINT NOT NULL DEFAULT 1 COMMENT '1-可发帖 0-禁止' AFTER banned_at,
  ADD COLUMN perm_comment TINYINT NOT NULL DEFAULT 1 COMMENT '1-可留言/评论/私信 0-禁止' AFTER perm_post,
  ADD COLUMN perm_order TINYINT NOT NULL DEFAULT 1 COMMENT '1-可下单 0-禁止' AFTER perm_comment,
  ADD COLUMN perm_broadcast TINYINT NOT NULL DEFAULT 1 COMMENT '1-可广播系统通知 0-禁止' AFTER perm_order;
```

```bash
cd /Users/katisarrow/summer
docker compose up -d --build
```

## Docker 停止

```bash
cd /Users/katisarrow/summer
docker compose down
```

## 本地开发启动

启动商品服务前在对应终端设置：

```bash
export AI_API_KEY="你的API Key"
export AI_BASE_URL="https://api.openai.com/v1"
export AI_MODEL="gpt-4.1-mini"
export AI_SUPPORTS_VISION="true"
export PRODUCT_IMAGE_DIR="/Users/katisarrow/summer/data/product-images"
export JWT_SECRET="$(openssl rand -base64 48)"
export INTERNAL_API_TOKEN="$(openssl rand -base64 48)"
export AI_CONFIG_ENCRYPTION_KEY="$(openssl rand -base64 48)"
export SPRING_DATASOURCE_PASSWORD="campus123"
```

```bash
cd /Users/katisarrow/summer
docker compose -f docker-compose-mw.yml up -d
```

```bash
cd /Users/katisarrow/summer/campus-trade
mvn install -DskipTests
```

分别打开 4 个终端运行：

```bash
cd /Users/katisarrow/summer/campus-trade
mvn -pl campus-gateway spring-boot:run
```

```bash
cd /Users/katisarrow/summer/campus-trade
mvn -pl campus-user spring-boot:run
```

```bash
cd /Users/katisarrow/summer/campus-trade
mvn -pl campus-product spring-boot:run
```

```bash
cd /Users/katisarrow/summer/campus-trade
mvn -pl campus-order spring-boot:run
```

```bash
cd /Users/katisarrow/summer/campus-trade-web
npm run dev
```

## 可选演示数据

仅限本地开发；生产环境禁止执行。演示数据不包含管理员账号，也不会覆盖已有用户密码。

```bash
mysql -h 127.0.0.1 -P 3306 -u campus -pcampus123 campus_trade < sql/seed-dev.sql
```
