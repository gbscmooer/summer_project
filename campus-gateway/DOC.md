# 网关服务文档 (campus-gateway)

`campus-gateway` 充当系统对外的唯一流量入口（端口：8080），负责路由分发、客户端 Token 统一鉴权以及用户信息透明注入。

---

## 1. 网关路由配置 (`application.yml`)

外部请求通过网关时，网关会剥离 `/api` 前缀并路由到对应的后端微服务（通过 Nacos 负载均衡）：

* **服务端口**：8080
* **路由映射**：
  * `/api/user/**` → `lb://campus-user`（剥离前缀，下游映射 `/user/**`）
  * `/api/product/**` → `lb://campus-product`（剥离前缀，下游映射 `/product/**`）
  * `/api/order/**` → `lb://campus-order`（剥离前缀，下游映射 `/order/**`）

---

## 2. 全局鉴权拦截器 (`AuthGlobalFilter`)

### 2.1 鉴权白名单 (无需登录)
以下接口在网关处直接放行，不校验 Token：
- `POST /api/user/register` (用户注册)
- `POST /api/user/login` (用户登录)
- `GET  /api/product/list` (商品分页分类列表)
- `GET  /api/product/search` (商品 ES 搜索引擎接口)
- `GET  /api/product/{id}` (商品详情，纯数字 ID 匹配)

### 2.2 防越权过滤 (剥离 X-User-Id)
- **机制**：为了防止攻击者在 HTTP Header 中伪造 `X-User-Id` 越权访问，网关在**所有请求**（包含白名单）进入路由链的第 1 步，强行移除名为 `X-User-Id` 的请求头。

### 2.3 JWT 校验与透传
对于非白名单请求：
1. 从请求头 `Authorization` 中提取 `Bearer {token}`。
2. 校验 Token 签名和时效。若失败，直接返回 HTTP 401 状态码，输出 JSON 响应：
   ```json
   { "code": 401, "message": "未登录或Token已失效", "data": null }
   ```
3. 若校验通过，使用 `JwtUtil.parseUserId(token)` 解析出 `userId`。
4. 在即将下发到下游微服务的 Request Header 中注入 `X-User-Id: {userId}`。
5. 下游微服务（User/Product/Order）从请求头中直接读取 `X-User-Id` 即可获取当前登录用户的 ID，**不再需要重复解析 Token**。
