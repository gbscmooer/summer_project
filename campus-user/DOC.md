# 用户服务文档 (campus-user)

`campus-user` 微服务负责用户的账号注册、密码加密存储、JWT 登录签发以及个人信息的获取与更新。

---

## 1. 数据库设计 (`t_user` 表)

- **主表**：`t_user`
- **字段明细**：
  | 字段名 | 类型 | 说明 |
  | :--- | :--- | :--- |
  | id | bigint PK | 用户ID，自增 |
  | username | varchar(50) | 用户名，必须唯一 |
  | password | varchar(100) | 密码，使用 BCrypt 强哈希加密存储 |
  | nickname | varchar(50) | 用户昵称 |
  | avatar | varchar(255) | 头像 URL |
  | phone | varchar(20) | 联系电话 |
  | create_time | datetime | 账号创建时间 |
  | update_time | datetime | 账号更新时间 |

---

## 2. 对外 API 规划 (需经过网关)

所有接口统一以 `/api/user` 作为前缀，网关转发时剥离。

### 2.1 用户注册 (白名单)
- **接口**：`POST /api/user/register`
- **入参**：`{ "username", "password", "nickname", "phone" }`
- **返回**：`{ "code": 200, "message": "注册成功", "data": { "userId" } }`
- **失败情况**：用户名重复时，返回 `code: 1001` (用户名已存在)。

### 2.2 用户登录 (白名单)
- **接口**：`POST /api/user/login`
- **入参**：`{ "username", "password" }`
- **返回**：
  ```json
  {
    "code": 200,
    "message": "登录成功",
    "data": {
      "token": "eyJhbGciOiJIUzI1NiJ9...",
      "userId": 1001,
      "nickname": "张三",
      "avatar": "https://..."
    }
  }
  ```

### 2.3 获取当前登录用户信息 (需鉴权)
- **接口**：`GET /api/user/info`
- **说明**：下游从 Request Header 读取网关注入的 `X-User-Id`。
- **返回**：`{ "code": 200, "data": { "userId", "username", "nickname", "avatar", "phone", "createTime" } }`

### 2.4 更新个人信息 (需鉴权)
- **接口**：`PUT /api/user/info`
- **入参**：`{ "nickname", "avatar", "phone" }` (支持部分字段更新)
- **返回**：`{ "code": 200, "message": "更新成功", "data": null }`

---

## 3. 内部 RPC 接口 (服务间调用)

此接口不经过网关，不在白名单中，不对前端开放。供 `campus-order` 服务批量组装买家/卖家昵称使用。

### 3.1 批量查询用户简要信息
- **接口**：`GET /user/batch?ids=1001,1002`
- **返回**：
  ```json
  {
    "code": 200,
    "message": "success",
    "data": [
      { "userId": 1001, "nickname": "张三", "phone": "13800138001" },
      { "userId": 1002, "nickname": "李四", "phone": "13800138002" }
    ]
  }
  ```
