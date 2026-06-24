# 公共模块文档 (campus-common)

`campus-common` 提供了整个微服务体系的公共基础类，包括统一响应结构、全局异常处理、JWT 鉴权解析工具等。

---

## 1. 统一响应格式 (`Result`)

为了统一前后端通信，所有微服务接口必须返回 `Result<T>` 格式的 JSON 响应，即使在异常情况下也不例外。

### 1.1 核心数据结构

- **类路径**：`com.campus.common.result.Result`
- **JSON 结构**：
  ```json
  {
    "code": 200,
    "message": "success",
    "data": { }
  }
  ```

### 1.2 业务状态码 (`ResultCode`)

- **类路径**：`com.campus.common.result.ResultCode`
- 常用状态码定义：
  | code | 含义 |
  | :--- | :--- |
  | 200 | 成功 |
  | 400 | 参数错误 |
  | 401 | 未登录 / Token 失效 |
  | 403 | 无权限 |
  | 404 | 资源不存在 |
  | 500 | 服务器内部错误 |
  | 1001 | 用户名已存在 |
  | 1002 | 用户名或密码错误 |
  | 2001 | 商品不存在 |
  | 2002 | 商品已下架/已售 |
  | 2003 | 库存不足 |
  | 3001 | 订单不存在 |
  | 3002 | 不能购买自己的商品 |
  | 3003 | 订单状态不允许此操作 |

---

## 2. 统一异常处理 (`GlobalExceptionHandler`)

### 2.1 业务异常 (`BizException`)
- **定义**：继承自 `RuntimeException`，带有 `code` 业务状态码。
- **作用**：业务开发过程中需要阻断逻辑并返回错误提示时直接 `throw new BizException(ResultCode.XXX)`。

### 2.2 全局拦截器 (`GlobalExceptionHandler`)
- **定义**：使用 `@RestControllerAdvice` 声明。
- **职责**：
  - 拦截 `BizException` 并转为 `Result.error(code, msg)` 返回。
  - 拦截 `@Valid` 参数校验异常（`MethodArgumentNotValidException`），返回 `Result.error(400, "参数格式错误")`。
  - 拦截未知系统异常（`Exception`），打印堆栈日志并返回 `Result.error(500, "服务器内部异常")`。

---

## 3. JWT 鉴权工具 (`JwtUtil`)

- **类路径**：`com.campus.common.util.JwtUtil`
- **主要方法**：
  - `generateToken(Long userId)`：生成包含 `userId` 载荷且生命周期为 7 天的 JWT Token。
  - `isValid(String token)`：校验 Token 的签名及有效期。
  - `parseUserId(String token)`：解析并返回 Token 中的 `userId`。
