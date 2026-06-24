# 校园二手交易平台 —— API 接口规划（全局概览）

为了避免单个大文档在开发时塞爆 AI 助手的上下文空间，本项目的所有具体接口定义均已按服务边界**拆分并下沉到对应子模块文件夹中**。

开发特定服务时，**请直接阅读该模块目录下的 `DOC.md` 文档**。

---

## 📂 接口文档目录索引

1. **用户服务接口**：包含注册、登录、个人中心、服务间批量查询昵称。
   * 👉 详见：[campus-user/DOC.md 章节二、三](file:///Users/katisarrow/summer/campus-user/DOC.md)
2. **商品服务接口**：包含发布/修改/下架商品、详情、分类列表、ES全文搜索、Feign库存扣减。
   * 👉 详见：[campus-product/DOC.md 章节三、四](file:///Users/katisarrow/summer/campus-product/DOC.md)
3. **订单与通知服务接口**：包含常规下单、订单状态机流转、高并发秒杀抢购削峰、订单轮询以及通知管理。
   * 👉 详见：[campus-order/DOC.md 章节四](file:///Users/katisarrow/summer/campus-order/DOC.md)

---

## 1. 全局设计规范（所有接口适用）

### 1.1 HTTP 约定
所有经过网关路由的外部接口都必须符合 RESTful API 动作约定。
所有的外部请求统一携带前缀 `/api/`，网关在转发给微服务时自动剥离前缀。

### 1.2 统一响应格式
所有接口一律返回 HTTP 状态码 200，并采用以下格式封装：
```json
{
  "code": 200,
  "message": "success",
  "data": { }
}
```
* **非成功业务状态码**：使用统一的 `code` 区分。详细的状态码值和代表的业务异常，请阅读：[campus-common/DOC.md 章节一](file:///Users/katisarrow/summer/campus-common/DOC.md)。

### 1.3 统一鉴权与身份透传
- **JWT token**：登录成功会向前端返回 Bearer token。
- **网关校验**：凡非白名单接口，网关 `AuthGlobalFilter` 自动校验 token。
- **Header 传递**：校验成功后网关剥离可能存在的越权头，并将真实 `userId` 写入 `X-User-Id` 头传递到下游微服务。下游服务通过 `@RequestHeader("X-User-Id")` 即可安全获取当前用户的唯一标识。
