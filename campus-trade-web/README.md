# 校园淘 · CampusTrade 前端

校园二手交易平台的前端单页应用（SPA）。

技术栈：**Vue 3 + Vite 5 + Vue Router 4 + Pinia 2 + Axios + Element Plus**。

> 全程对接真实后端接口，无任何 mock 数据。

## 快速开始

```bash
# 1. 安装依赖
npm install

# 2. 启动开发服务器（默认 http://localhost:5173）
npm run dev

# 3. 生产构建
npm run build

# 4. 本地预览构建产物
npm run preview
```

启动后访问 <http://localhost:5173>。

## 功能模块

| 路由 | 页面 | 说明 | 是否需登录 |
| --- | --- | --- | --- |
| `/` | 首页 | 商品网格 + 分类筛选 + 分页 | 否 |
| `/product/:id` | 商品详情 | 图片轮播、价格、描述、卖家信息；立即购买（占位） | 否 |
| `/login` | 登录 | 真实登录，token 存 Pinia + localStorage | 否 |
| `/register` | 注册 | 真实注册，成功后跳登录 | 否 |
| `/publish` | 发布商品 | 发布闲置表单 | **是** |
| `/my` | 个人中心 | 个人信息（可编辑）+ 我发布的（可下架） | **是** |

## 后端接口与代理配置

前端统一通过 `baseURL = /api` 发起请求，由 Vite dev proxy 转发到后端服务。

### P3 单体阶段（当前）

各微服务独立部署，前端直连，proxy 配置见 `vite.config.js`：

| 前缀 | 目标服务 | 端口 |
| --- | --- | --- |
| `/api/user` | 用户服务 | 8081 |
| `/api/product` | 商品服务 | 8082 |
| `/api/order` | 订单服务（暂未使用） | 8083 |

> 后端 controller 路径不带 `/api` 前缀，因此 proxy 配置了 `rewrite` 去掉 `/api` 再转发。

### P4 网关阶段（后续切换）

网关上线后，把 `vite.config.js` 中的三条 proxy 整体替换为单条即可（网关自身 `StripPrefix` 去掉 `/api`，无需前端 rewrite）：

```js
server: {
  port: 5173,
  proxy: {
    '/api': { target: 'http://localhost:8080', changeOrigin: true }
  }
}
```

## 统一响应格式

所有接口返回信封结构：

```json
{ "code": 200, "message": "...", "data": {} }
```

- `code === 200`：业务成功；
- `code === 401`：登录态失效，前端自动清 token 并跳转 `/login`；
- 其它 code：弹出 `message` 提示（如 `1001` 用户名已存在、`1002` 用户名或密码错误、`2001` 商品不存在）。

响应拦截器统一在 `src/api/request.js` 中处理。

## 目录结构

```
campus-trade-web/
├── index.html
├── vite.config.js          # dev proxy + @ 别名
├── package.json
└── src/
    ├── main.js             # 入口：Pinia → Router → Element Plus
    ├── App.vue             # 顶部导航栏 + <router-view>
    ├── styles.css          # 全局样式
    ├── api/
    │   ├── request.js      # axios 实例 + 拦截器
    │   ├── user.js         # 用户接口
    │   └── product.js      # 商品接口
    ├── router/index.js     # 路由表 + 登录守卫
    ├── store/user.js       # Pinia user store（持久化 localStorage）
    ├── constants/product.js# 分类 / 状态常量
    └── views/              # 6 个页面
        ├── Login.vue
        ├── Register.vue
        ├── Home.vue
        ├── ProductDetail.vue
        ├── Publish.vue
        └── My.vue
```
