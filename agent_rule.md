# Agent Rule

## 工作范围
- 维护后端微服务：`campus-trade/campus-common`、`campus-trade/campus-gateway`、`campus-trade/campus-user`、`campus-trade/campus-product`、`campus-trade/campus-order`，以及 `campus-product` 内独立的 AI Core 与图片存储层。
- 维护前端工程：`campus-trade-web`。
- 维护部署、数据库和交付文档：`docker-compose.yml`、`docker-compose-mw.yml`、`sql/`、`README.md`、`document.md`、各模块 `DOC.md`。

## 工作限制
- 外部 HTTP 接口只允许使用 `GET` 和 `POST`；CORS 仅额外允许预检 `OPTIONS`。
- 不允许在业务代码或前端 API 层使用 mock、fake、dummy 数据或本地伪造响应。
- 默认数据库初始化脚本只建表；演示数据必须放在单独的可选 seed 文件中。
- 不直接修改用户未要求的历史提交、分支、远端或标签。
- 不提交构建产物、依赖目录、日志、临时文件和本地密钥。
- AI 密钥只允许来自环境变量或管理员配置接口（库内存储、接口响应必须脱敏）；AI 结果必须作为待确认建议，禁止绕过卖家确认直接发布商品。
- 管理员能力以 `t_user.role=1` 判定；禁止仓库提供默认管理员或默认管理员密码。

## 代码约束
- 后端控制器层只负责路由、参数接收和响应封装；业务逻辑放在 Service/Core 层。
- 前端页面只调用 `src/api/` 中的接口封装，不直接散落 axios 请求。
- 接口响应统一使用 `{ code, message, data }`。
- 登录态由网关校验 JWT 后注入 `X-User-Id`；下游服务不信任客户端直接传入的 `X-User-Id`。
- 涉及前后端联调的变更必须验证 CORS、鉴权和错误响应。
- AI 搜索必须查询真实商品服务；不得让模型生成不存在的商品结果或伪造市场价格。

## 交付约束
- 后端变更必须通过 `mvn test`。
- 前端变更必须通过 `npm test` 和 `npm run build`。
- 接口方法约束由测试脚本自动检查，不允许只靠人工审查。
- Docker 交付前需先生成后端 jar 与前端 dist，再执行 Compose 构建或启动验证。
