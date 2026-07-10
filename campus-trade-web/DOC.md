# 前端与部署文档 (campus-trade-web)

`campus-trade-web` 是一个基于 Vue 3 + Vite 构建的前端单页应用 (SPA)。本模块文档涵盖了前端工程的项目结构、核心网络封装、页面与路由规划以及基于 Nginx 的 Docker 生产部署配置。

---

## 1. 前端工程结构概览

```text
campus-trade-web/
├── src/
│   ├── api/                       # axios 网络请求集，对应后端各个微服务
│   │   ├── user.js                # 用户登录、注册、修改信息请求
│   │   ├── product.js             # 商品发布、详情、ES 搜索等请求
│   │   └── order.js               # 下单、秒杀、通知管理请求
│   ├── views/                     # 视图页面
│   │   ├── Login.vue              # 登录页面
│   │   ├── Register.vue           # 注册页面
│   │   ├── Home.vue               # 首页列表（支持分类Tab、ES检索过滤）
│   │   ├── ProductDetail.vue      # 商品详情与常规/秒杀下单流程
│   │   ├── Publish.vue            # 发布商品与修改表单
│   │   ├── My.vue                 # 个人中心（我买的/我卖的商品与订单列表）
│   │   └── Orders.vue             # 订单处理详情页
│   ├── router/                    # Vue Router 路由配置，包含全局导航守卫
│   ├── store/                     # Pinia 状态管理，存储用户身份 Token 状态
│   ├── constants/                 # 常量定义（例如分类列表）
│   ├── main.js                    # 项目入口启动配置
│   └── styles.css                 # 全局基础 UI 样式文件
├── nginx.conf                     # 生产环境下 Nginx 的转发与托管配置
└── Dockerfile                     # 前端生产环境 Docker 容器打包脚本
```

---

## 2. Axios 请求封装与统一鉴权

- **封装路径**：`src/api/request.js` (或者通过 Axios 统一创建拦截器)
- **请求拦截器 (Request Interceptor)**：
  - **职责**：自动从 Pinia / LocalStorage 中获取用户的登录 JWT `token`。如果 token 存在，在 HTTP 请求头中自动附加：
    ```http
    Authorization: Bearer {token}
    ```
- **响应拦截器 (Response Interceptor)**：
  - **职责**：拦截异常响应状态，当后端返回 `code: 401` 或网关拦截器抛出 401 错误时，清空当前用户的 Token 并强制跳转到 `/login` 登录页，引导用户重新登录。

---

## 3. 首页 (Home.vue) 搜索与过滤逻辑

在首页 `Home.vue` 中集成了常规列表与 ES 全文检索过滤器的平滑切换：
* **无关键词与无筛选**：请求 [api/product.js](file:///Users/katisarrow/summer/campus-trade-web/src/api/product.js) 中的 `getProductList` 接口，向网关索取常规 MySQL 分页分类商品。
* **输入关键词**：自动向网关发送 `searchProducts` 请求，走 Elasticsearch 完成分词检索；分类条件会随列表或搜索请求一起发送。

---

## 4. 生产环境托管与 Docker 部署

在项目打包为静态资源后，通过轻量级 Nginx 容器运行部署。

### 4.1 Nginx 配置文件 ([nginx.conf](file:///Users/katisarrow/summer/campus-trade-web/nginx.conf))
- **端口**：80
- **静态资源托管**：根路径指向 `/usr/share/nginx/html`。为支持 Vue Router 的 HTML5 History 模式，配置 `try_files`，在未匹配到静态物理文件时回退到 `index.html`：
  ```nginx
  location / {
      root   /usr/share/nginx/html;
      index  index.html index.htm;
      try_files $uri $uri/ /index.html;
  }
  ```
- **网关反向代理**：将所有 `/api/` 前缀的请求利用 Nginx 反向代理转发到后端的 Spring Cloud Gateway (微服务内部容器互联地址 `campus-gateway:8080`)，从而解决开发和生产中的跨域问题：
  ```nginx
  location /api/ {
      proxy_pass http://campus-gateway:8080;
      proxy_set_header Host $host;
      proxy_set_header X-Real-IP $remote_addr;
      proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
  }
  ```

### 4.2 Dockerfile 打包机制 ([Dockerfile](file:///Users/katisarrow/summer/campus-trade-web/Dockerfile))
- **单阶段 Nginx 镜像**：
  1. 本地先执行 `npm install`、`npm test`、`npm run build` 生成 `dist/`。
  2. Dockerfile 使用 `nginx:1.25-alpine`，将本地 `dist/` 复制到 `/usr/share/nginx/html`，并将 [nginx.conf](file:///Users/katisarrow/summer/campus-trade-web/nginx.conf) 覆盖到容器 `/etc/nginx/nginx.conf`，暴露 80 端口启动。
