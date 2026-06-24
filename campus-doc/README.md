# 校园二手交易平台 (CampusTrade)

本项目是一个基于 Spring Cloud 微服务架构与 Vue3 前端构建的校园二手交易系统。

为了降低 AI 开发时的上下文噪音，我们采用了**树状多层级文档体系**。系统文档已根据模块和职责进行了拆分，使子代理/开发者在工作时只需加载当前开发模块下的文档。

---

## 📂 文档树状结构 (Sitemap)

```text
/ (项目根目录)
├── campus-doc/                      # 全局文档父文件夹
│   ├── README.md                    # 本文档：全局概览与文档索引
│   ├── 开发任务清单.md               # 全局开发任务清单（已完成 T0.1 ~ T8.1）
│   ├── 架构设计.md                  # 全局技术栈规划与微服务部署概览（极简版）
│   ├── API接口规划.md               # 全局API设计准则与公共网关鉴权说明（极简版）
│   └── tasks/                       # 历史与未完成任务卡片目录
├── sql/
│   └── init.sql                     # MySQL 数据库建表与初始化脚本
├── campus-common/
│   └── DOC.md                       # 公共服务文档 (Result模型, 异常捕获, JWT工具)
├── campus-gateway/
│   └── DOC.md                       # 网关服务文档 (路由配置, JWT全局鉴权拦截器)
├── campus-user/
│   └── DOC.md                       # 用户服务文档 (t_user表, 登录注册个人信息接口)
├── campus-product/
│   └── DOC.md                       # 商品服务文档 (t_product表, Redis三兄弟缓存, ES搜索引擎)
├── campus-order/
│   └── DOC.md                       # 订单服务文档 (t_order/t_notification表, RabbitMQ, Feign, 秒杀)
└── campus-trade-web/
    └── DOC.md                       # 前端工程文档 (Vue3页面结构, Axios封装, 部署Nginx)
```

---

## 🛠️ 模块目录索引

点击下方链接可直达各层级子文档：

1. **公共基石层**：[campus-common/DOC.md](file:///Users/katisarrow/summer/campus-common/DOC.md) — 统一接口规范及通用工具集。
2. **流量网关层**：[campus-gateway/DOC.md](file:///Users/katisarrow/summer/campus-gateway/DOC.md) — 网关路由及统一 JWT 鉴权规则。
3. **用户业务层**：[campus-user/DOC.md](file:///Users/katisarrow/summer/campus-user/DOC.md) — 注册登录及个人信息模块。
4. **商品业务层**：[campus-product/DOC.md](file:///Users/katisarrow/summer/campus-product/DOC.md) — 商品管理、Redis 高可用缓存与 Elasticsearch 检索模块。
5. **交易与通知层**：[campus-order/DOC.md](file:///Users/katisarrow/summer/campus-order/DOC.md) — 下单链路、RabbitMQ 异步通知、OpenFeign 调用与 Redis+MQ 高并发秒杀削峰模块。
6. **前端展示层**：[campus-trade-web/DOC.md](file:///Users/katisarrow/summer/campus-trade-web/DOC.md) — 前端 SPA 工程结构及 Nginx 托管方案。

---

## 📈 当前项目主线完成度
- **P0 ~ P5 (基础/网关/Feign/订单业务)**：100% 完成并合并。
- **P6 (中间件增强 - Redis/ES/RabbitMQ)**：100% 完成，商品缓存、商品搜索、下单通知及秒杀功能皆已就绪。
- **P7 (部署与压测)**：100% 完成，已交付各模块 Dockerfile、一键部署 [docker-compose.yml](file:///Users/katisarrow/summer/docker-compose.yml) 与 JMeter 压测脚本 [campus_trade_performance.jmx](file:///Users/katisarrow/summer/campus_trade_performance.jmx)。
- **P8 (秒杀/答辩准备)**：秒杀削峰（T8.1）已交付，剩余答辩材料整理。
