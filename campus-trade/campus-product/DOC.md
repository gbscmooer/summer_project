# 商品服务文档 (campus-product)

`campus-product` 微服务负责商品发布、图片存储、修改、下架、MySQL持久化、Redis 缓存、Elasticsearch 全文检索，以及独立 AI Core 提供的自然语言检索与多图发布草稿。

---

## 1. 数据库与搜索引擎索引设计

### 1.1 MySQL 物理表设计 (`t_product`)
| 字段名 | 类型 | 说明 |
| :--- | :--- | :--- |
| id | bigint PK | 商品ID，自增 |
| title | varchar(100) | 商品标题 |
| description | text | 商品详细描述 |
| price | decimal(10,2) | 商品标价 |
| images | varchar(1000) | 图片列表，以半角逗号分隔 |
| category | varchar(50) | 商品分类（如数码/生活/教材等） |
| seller_id | bigint | 卖家（发布者）的 ID |
| status | tinyint | 状态：0-下架，1-在售，2-已售 |
| stock | int | 库存（二手商品通常为 1） |
| view_count | int | 商品总浏览量（读取时自增） |
| create_time | datetime | 发布时间 |

### 1.2 Elasticsearch 索引文档 (`ProductDocument`)
- **索引名称**：`product`
- **文档实体映射字段**：
  * `id` (Long) — 主键
  * `title` (String, text 类型，支持 ik_max_word / ik_smart 分词)
  * `description` (String, text 类型，同分词器)
  * `price` (Double) — 价格过滤和排序
  * `cover` (String) — 列表展示封面图
  * `category` (String, keyword 类型) — 分类过滤
  * `status` (Integer) — 状态检索（仅索取 `status = 1`）
  * `createTime` (Long) — 时间戳，便于按时间排序

---

## 2. Redis 详情缓存设计 (Cache-Aside 旁路缓存)

为保障详情接口 `GET /api/product/{id}` 能够承受高并发读取，采用多重 Redis 防护策略。

### 2.1 缓存穿透防御
- **逻辑**：如果查询一个数据库和缓存中皆不存在的商品（如 `id = -99`），系统会在缓存中写入一个特定的空标记 `NullValueMarker`，设置较短生存时间（如 60 秒），防止相同请求穿透直击数据库。

### 2.2 缓存击穿防御 (互斥锁重建)
- **逻辑**：在缓存未命中（Cache Miss）时，系统不允许多个线程并发直查数据库，而是尝试通过 Redis 的 `SETNX` 抢占分布式锁：
  * **抢锁成功**：执行数据库查询，并回填缓存（携带防雪崩的随机 TTL），随后释放锁。
  * **抢锁失败**：线程休眠数毫秒并轮询重试读取缓存，若超出重试次数，则最终兜底查库，避免核心流程被无限阻塞。

### 2.3 缓存雪崩防御
- **逻辑**：每个真实的商品详情回填缓存时，TTL 计算为 `DETAIL_TTL_BASE` (如 30 分钟) 加上一个 `0~5分钟` 的随机毫秒值，确保大批详情缓存不会在同一时间段内集中失效。

### 2.4 热门商品缓存预热
- **类**：`ProductCachePreheatRunner` (`CommandLineRunner`)
- **逻辑**：在服务启动时，主动查询 MySQL 中在售商品中**浏览量前 100 名**的数据，并将其批量载入 Redis 中。

---

## 3. 对外 API 接口

### 3.1 发布商品 (需登录)
- **接口**：`POST /api/product`
- **逻辑**：数据写入 MySQL 并在成功后**同步双写**写入 ES。

### 3.2 修改商品 (需登录且仅卖家本人)
- **接口**：`POST /api/product/{id}/update`
- **逻辑**：更新 MySQL，清除商品详情缓存与秒杀预热缓存；商品仍在售时同步写入 ES，非在售时从 ES 删除。

### 3.3 下架商品 (需登录且仅卖家本人)
- **接口**：`POST /api/product/{id}/delete`
- **逻辑**：MySQL 状态设为 0，**从 ES 索引中删除该商品**，并清除 Redis 详情缓存。

### 3.4 商品详情 (白名单)
- **接口**：`GET /api/product/{id}`
- **逻辑**：走详情缓存逻辑（见第二章），并通过用户服务内部接口补全 `sellerNickname`。

### 3.5 分类列表分页查询 (白名单)
- **接口**：`GET /api/product/list?pageNum=1&pageSize=10&category=教材`
- **逻辑**：直查 MySQL 在售（`status = 1`）列表，带分页。

### 3.6 ES 商品搜索 (白名单)
- **接口**：`GET /api/product/search?keyword=高数&category=教材&minPrice=0&maxPrice=100&sort=price_asc`
- **逻辑**：优先使用 `CriteriaQuery` 构造器组合多级过滤和排序规则在 Elasticsearch 检索；ES 不可用时降级为 MySQL 模糊查询。

### 3.7 商品图片上传与读取
- **上传**：`POST /api/product/image/upload`，需登录，multipart 字段 `files`，1～5 张，支持 JPG/PNG/WEBP，单张不超过 8MB；写入 `t_product_image` 记录上传者。
- **删除**：`POST /api/product/image/delete`，需登录，仅可删除本人上传的图片。
- **读取**：`GET /api/product/image/{filename}`，公开访问。
- **存储**：目录由 `PRODUCT_IMAGE_DIR` 配置，文件名使用随机 UUID，路径解析禁止目录穿越。

### 3.8 AI 自然语言检索
- **接口**：`POST /api/ai/search`，需登录。
- **入参**：`query`、`pageSize`。
- **逻辑**：模型仅解析检索意图，商品候选由 `ProductService.search` 返回，禁止生成不存在的商品。

### 3.9 AI 多图发布草稿
- **接口**：`POST /api/ai/listing-draft`，需登录。
- **入参**：站内 `images` 地址数组和可选 `notes`。
- **逻辑**：校验图片归属后，视觉模型最多读取前 3 张提取商品信息；Core 层查询站内同类商品，按价格分布和成色计算建议价。返回草稿，不直接发布。

### 3.10 管理员 AI 配置
- **接口**：`GET/POST /api/admin/ai-config`，需 `role=1` 管理员。
- **逻辑**：配置写入 `t_ai_config`，启用后覆盖环境变量并立即生效；响应中 API Key 脱敏，空 Key 表示保留原值。

### 3.11 AI 环境变量（兜底）
| 变量 | 默认值 | 说明 |
| :--- | :--- | :--- |
| AI_API_KEY | 空 | 环境变量兜底；管理端也可配置 |
| AI_BASE_URL | https://api.openai.com/v1 | OpenAI-compatible 根地址 |
| AI_MODEL | gpt-4.1-mini | JSON 与视觉模型 |
| AI_SUPPORTS_VISION | true | 是否支持图片消息；不支持时发布草稿返回 4004 |
| AI_TIMEOUT_SECONDS | 60 | 调用超时秒数 |
| PRODUCT_IMAGE_DIR | ./data/product-images | 图片存储目录 |

---

## 4. 内部服务间接口

### 4.1 获取单个商品信息 (Feign)
- **接口**：`GET /product/inner/{id}`

### 4.2 扣减商品库存 (Feign)
- **接口**：`POST /product/inner/{id}/deduct?preserveSeckillCache=false`
- **说明**：下单时由 `campus-order` 服务调用。若库存被扣减为 0，本服务会自动将其状态设为 `2`（已售）。秒杀消费者扣减 DB 库存时传 `preserveSeckillCache=true`，避免删除 Redis 秒杀预扣库存。

### 4.3 恢复/回滚商品库存 (Feign)
- **接口**：`POST /product/inner/{id}/restore`
- **说明**：订单取消或下单补偿时恢复库存；商品未下架时将状态恢复为 `1`（在售），已下架商品只恢复库存不重新上架。
