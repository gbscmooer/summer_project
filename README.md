# 启动与安装

## 环境

- JDK 17
- Maven 3.9+
- Node.js 18+
- Docker Desktop 或兼容 Docker Compose 的运行环境

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

```bash
mysql -h 127.0.0.1 -P 3306 -u campus -pcampus123 campus_trade < sql/seed-dev.sql
```
