package com.campus.product.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.campus.common.constant.UserRole;
import com.campus.common.exception.BizException;
import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import com.campus.common.util.PageParamUtil;
import com.campus.product.constant.ProductCacheConstants;
import com.campus.product.dto.NullValueMarker;
import com.campus.product.dto.PublishQuotaVO;
import com.campus.product.dto.SellerProductStatsView;
import com.campus.product.dto.SellerProductDashboardView;
import com.campus.product.dto.ProductDetailVO;
import com.campus.product.dto.ProductListVO;
import com.campus.product.dto.ProductRequest;
import com.campus.product.entity.Product;
import com.campus.product.entity.StockDeductionLogEntity;
import com.campus.product.entity.StockRestoreLogEntity;
import com.campus.product.es.ProductDocument;
import com.campus.product.es.ProductRepository;
import com.campus.product.feign.UserFeignClient;
import com.campus.product.feign.dto.UserBriefDTO;
import com.campus.product.mapper.ProductMapper;
import com.campus.product.mapper.StockDeductionLogMapper;
import com.campus.product.mapper.StockRestoreLogMapper;
import com.campus.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ProductRepository productRepository;
    private final ElasticsearchTemplate elasticsearchTemplate;
    private final UserFeignClient userFeignClient;
    private final StockDeductionLogMapper stockDeductionLogMapper;
    private final StockRestoreLogMapper stockRestoreLogMapper;

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final ConcurrentHashMap<Long, Object> localLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, LocalCacheEntry> localCache = new ConcurrentHashMap<>();
    private volatile long lastRedisErrorTime = 0L;
    private static final long REDIS_COOLDOWN_MS = 5000L;

    private static class LocalCacheEntry {
        final ProductDetailVO data;
        final boolean notFound;
        final long expireTime;

        LocalCacheEntry(ProductDetailVO data, boolean notFound, long ttlMs) {
            this.data = data;
            this.notFound = notFound;
            this.expireTime = System.currentTimeMillis() + ttlMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }

    // ==================== 商品写操作（含 ES 双写） ====================

    @Override
    public Long publish(Long sellerId, ProductRequest request) {
        requireMerchantOrAdmin(sellerId);
        Product product = new Product();
        product.setSellerId(sellerId);
        product.setTitle(request.getTitle());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setImages(request.getImages());
        product.setCategory(request.getCategory());
        product.setStock(request.getStock());
        product.setStatus(1);
        product.setViewCount(0);
        save(product);
        // 双写 ES：新商品入索引（失败仅告警，以 MySQL 为准）
        saveToEs(product);
        return product.getId();
    }

    @Override
    public void update(Long sellerId, Long productId, ProductRequest request) {
        Product product = getAndCheckOwner(sellerId, productId);
        product.setTitle(request.getTitle());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setImages(request.getImages());
        product.setCategory(request.getCategory());
        if (request.getStock() != null) product.setStock(request.getStock());
        updateById(product);
        // 改商品后删缓存，保证下次读取到最新数据
        evictDetailCache(productId);
        evictSeckillCache(productId);
        // 双写 ES：仅在售商品保留索引，已下架/已售则删除索引
        syncToEs(product);
    }

    @Override
    public void remove(Long sellerId, Long productId) {
        Product product = getAndCheckOwner(sellerId, productId);
        product.setStatus(0);
        updateById(product);
        // 下架后删缓存
        evictDetailCache(productId);
        evictSeckillCache(productId);
        // 下架后从 ES 删除该文档，搜索只返回在售商品
        deleteFromEs(productId);
    }

    // ==================== 商品详情（Redis 缓存旁路 cache-aside） ====================

    @Override
    public ProductDetailVO getDetail(Long productId) {
        String cacheKey = ProductCacheConstants.detailKey(productId);

        // 1. 先查缓存
        Object cached = safeGetCache(cacheKey);
        if (cached != null) {
            // 命中空值标记 → 商品不存在（缓存穿透防护）
            if (cached instanceof NullValueMarker) {
                throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
            }
            if (cached instanceof ProductDetailVO vo) {
                baseMapper.incrementViewCount(productId);
                enrichSellerNickname(vo);
                return vo;
            }
        }

        // 2. 本地锁防击穿 (无论是 Redis 正常还是异常，本地锁都能起到单机排队防击穿作用)
        Object localLock = localLocks.computeIfAbsent(productId, k -> new Object());
        synchronized (localLock) {
            try {
                // 本地双重检查
                cached = safeGetCache(cacheKey);
                if (cached != null) {
                    if (cached instanceof NullValueMarker) {
                        throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
                    }
                    if (cached instanceof ProductDetailVO vo) {
                        baseMapper.incrementViewCount(productId);
                        enrichSellerNickname(vo);
                        return vo;
                    }
                }

                // 3. 未命中：去分布式锁重建，防止缓存击穿
                return loadWithMutex(productId, cacheKey);
            } finally {
                localLocks.remove(productId, localLock);
            }
        }
    }

    /**
     * 缓存击穿防护：SETNX 抢重建锁。
     * 抢到锁 → 查库回填后释放锁；没抢到 → 短暂 sleep 后重试读缓存（最多若干次），
     * 仍读不到则兜底直接查库（避免长时间阻塞）。
     */
    private ProductDetailVO loadWithMutex(Long productId, String cacheKey) {
        if (!isRedisHealthy()) {
            log.warn("Redis 已判定为不可用，本地锁保护下直接查库兜底，productId={}", productId);
            return loadFromDbAndCache(productId, cacheKey);
        }

        String lockKey = ProductCacheConstants.lockKey(productId);
        for (int attempt = 0; attempt <= ProductCacheConstants.LOCK_MAX_RETRY; attempt++) {
            boolean locked = tryLock(lockKey);
            if (locked) {
                try {
                    // 双重检查：拿到锁后再查一次缓存，可能已被前一个持锁线程回填
                    Object cached = safeGetCache(cacheKey);
                    if (cached instanceof NullValueMarker) {
                        throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
                    }
                    if (cached instanceof ProductDetailVO vo) {
                        baseMapper.incrementViewCount(productId);
                        enrichSellerNickname(vo);
                        return vo;
                    }
                    // 查库回填
                    return loadFromDbAndCache(productId, cacheKey);
                } finally {
                    unlock(lockKey);
                }
            }
            // 没抢到锁：sleep 后重试读缓存
            sleepQuietly(ProductCacheConstants.LOCK_RETRY_INTERVAL_MS);
            Object cached = safeGetCache(cacheKey);
            if (cached instanceof NullValueMarker) {
                throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
            }
            if (cached instanceof ProductDetailVO vo) {
                baseMapper.incrementViewCount(productId);
                enrichSellerNickname(vo);
                return vo;
            }
        }
        // 重试耗尽兜底：直接查库（不阻塞用户请求）
        log.warn("缓存重建锁竞争超时，降级直查 DB，productId={}", productId);
        return loadFromDbAndCache(productId, cacheKey);
    }

    /**
     * 查库 → 命中则写缓存（随机 TTL 防雪崩）并返回；
     * 不存在则写空值标记（短 TTL 防穿透）并抛 PRODUCT_NOT_FOUND。
     */
    private ProductDetailVO loadFromDbAndCache(Long productId, String cacheKey) {
        Product product = getById(productId);
        if (product == null) {
            // 缓存穿透防护：缓存空值，短 TTL
            safeSetCache(cacheKey, new NullValueMarker(), ProductCacheConstants.NULL_TTL.toMillis());
            if (!isRedisHealthy()) {
                localCache.put(productId, new LocalCacheEntry(null, true, 5000L));
            }
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }
        baseMapper.incrementViewCount(productId);
        product.setViewCount(product.getViewCount() + 1);
        ProductDetailVO vo = ProductDetailVO.from(product);
        enrichSellerNickname(vo);
        // 缓存不含卖家评分，避免 TTL 内信誉「冻住」；命中缓存时仍会强制刷新评分
        BigDecimal cachedRating = vo.getSellerAvgRating();
        Integer cachedReviewCount = vo.getSellerReviewCount();
        vo.setSellerAvgRating(null);
        vo.setSellerReviewCount(null);
        // 缓存雪崩防护：基础 30 分钟 + 随机 0~5 分钟，错峰过期
        safeSetCache(cacheKey, vo, randomDetailTtlMillis());
        vo.setSellerAvgRating(cachedRating);
        vo.setSellerReviewCount(cachedReviewCount);
        if (!isRedisHealthy()) {
            localCache.put(productId, new LocalCacheEntry(vo, false, 5000L));
        }
        return vo;
    }

    // ==================== Elasticsearch 搜索 ====================

    @Override
    public PageResult<ProductListVO> search(String keyword, String category,
                                            BigDecimal minPrice, BigDecimal maxPrice,
                                            String sort, Integer pageNum, Integer pageSize) {
        int page = PageParamUtil.normalizePageNum(pageNum);
        int size = PageParamUtil.normalizePageSize(pageSize);

        ensureTutorialNotInEs();
        try {
            NativeQuery query = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> {
                        // 仅在售且有库存（与首页市场一致）；排除新手教程商品
                        b.filter(f -> f.term(t -> t.field("status").value(1)));
                        b.filter(f -> f.range(r -> r.number(n -> n.field("stock").gt(0.0))));
                        b.mustNot(mn -> mn.term(t -> t.field("isTutorial").value(1)));
                        if (StringUtils.hasText(keyword)) {
                            // AND：中文 standard 分词下要求字都命中，避免“高数”扫到全站
                            b.must(m -> m.multiMatch(mm -> mm
                                    .query(keyword.trim())
                                    .fields("title^3", "description")
                                    .type(TextQueryType.BestFields)
                                    .operator(Operator.And)));
                        }
                        if (StringUtils.hasText(category)) {
                            b.filter(f -> f.term(t -> t.field("category").value(category)));
                        }
                        if (minPrice != null) {
                            b.filter(f -> f.range(r -> r.number(n -> n.field("price").gte(minPrice.doubleValue()))));
                        }
                        if (maxPrice != null) {
                            b.filter(f -> f.range(r -> r.number(n -> n.field("price").lte(maxPrice.doubleValue()))));
                        }
                        return b;
                    }))
                    .withPageable(PageRequest.of(page - 1, size, resolveSort(sort)))
                    .build();

            SearchHits<ProductDocument> hits = elasticsearchTemplate.search(query, ProductDocument.class);
            List<ProductListVO> list = hits.getSearchHits().stream()
                    .map(SearchHit::getContent)
                    .map(this::toListVO)
                    .collect(Collectors.toList());
            return PageResult.of(hits.getTotalHits(), page, size, list);
        } catch (Exception e) {
            log.warn("ES 搜索不可用，降级为 DB 模糊查询. keyword={}, category={}", keyword, category, e);
            return searchFromDb(keyword, category, minPrice, maxPrice, sort, page, size);
        }
    }

    private PageResult<ProductListVO> searchFromDb(String keyword, String category,
                                                   BigDecimal minPrice, BigDecimal maxPrice,
                                                   String sort, int page, int size) {
        Page<Product> dbPage = new Page<>(page, size);
        var queryChain = lambdaQuery()
                .eq(Product::getStatus, 1)
                .gt(Product::getStock, 0)
                .eq(StringUtils.hasText(category), Product::getCategory, category)
                .ge(minPrice != null, Product::getPrice, minPrice)
                .le(maxPrice != null, Product::getPrice, maxPrice);

        if (StringUtils.hasText(keyword)) {
            queryChain.and(q -> q.like(Product::getTitle, keyword).or().like(Product::getDescription, keyword));
        }

        if ("price_asc".equals(sort)) {
            queryChain.orderByAsc(Product::getPrice);
        } else if ("price_desc".equals(sort)) {
            queryChain.orderByDesc(Product::getPrice);
        } else {
            queryChain.orderByDesc(Product::getCreateTime);
        }

        queryChain.page(dbPage);
        List<ProductListVO> list = dbPage.getRecords().stream()
                .map(ProductListVO::from)
                .collect(Collectors.toList());
        return PageResult.of(dbPage.getTotal(), page, size, list);
    }

    /** sort 解析：price_asc/price_desc 按 price，time_desc（默认）按 createTime desc */
    private Sort resolveSort(String sort) {
        if ("price_asc".equals(sort)) {
            return Sort.by(Sort.Direction.ASC, "price");
        }
        if ("price_desc".equals(sort)) {
            return Sort.by(Sort.Direction.DESC, "price");
        }
        return Sort.by(Sort.Direction.DESC, "createTime");
    }

    /** ES 文档 → ProductListVO（与 /product/list 结构一致，createTime 从 epoch 毫秒转回 LocalDateTime） */
    private ProductListVO toListVO(ProductDocument doc) {
        ProductListVO vo = new ProductListVO();
        vo.setProductId(doc.getId());
        vo.setTitle(doc.getTitle());
        vo.setDescription(doc.getDescription());
        vo.setPrice(doc.getPrice() != null ? BigDecimal.valueOf(doc.getPrice()) : null);
        vo.setCover(doc.getCover());
        vo.setCategory(doc.getCategory());
        vo.setStatus(doc.getStatus());
        if (doc.getCreateTime() != null) {
            vo.setCreateTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(doc.getCreateTime()), ZONE));
        }
        return vo;
    }

    @Override
    public int reindexAll() {
        // 重建索引映射（含 stock 字段），避免旧 mapping 缺字段导致过滤失效
        try {
            var indexOps = elasticsearchTemplate.indexOps(ProductDocument.class);
            if (indexOps.exists()) {
                indexOps.delete();
            }
            indexOps.createWithMapping();
        } catch (Exception e) {
            log.warn("重建 ES 索引失败，尝试清空后继续刷入", e);
            try {
                productRepository.deleteAll();
            } catch (Exception ignored) {
                // ignore
            }
        }
        // 存量刷入：在售且有库存的普通商品（排除新手教程）
        List<Product> products = lambdaQuery()
                .eq(Product::getStatus, 1)
                .gt(Product::getStock, 0)
                .ne(Product::getIsTutorial, 1)
                .list();
        if (products.isEmpty()) {
            return 0;
        }
        List<ProductDocument> docs = products.stream()
                .map(ProductDocument::from)
                .collect(Collectors.toList());
        productRepository.saveAll(docs);
        log.info("ES 存量刷入完成，共 {} 条", docs.size());
        return docs.size();
    }

    @Override
    public void preheatHotProductsCache() {
        log.info("开始执行热门商品缓存预热...");
        // 1. 查询浏览量 Top N 且在售的商品
        List<Product> hotProducts = lambdaQuery()
                .eq(Product::getStatus, 1)
                .orderByDesc(Product::getViewCount)
                .last("limit " + ProductCacheConstants.WARM_UP_TOP_N)
                .list();

        if (hotProducts.isEmpty()) {
            log.info("没有需要预热的商品数据");
            return;
        }

        // 2. 写入 Redis
        for (Product product : hotProducts) {
            String cacheKey = ProductCacheConstants.detailKey(product.getId());
            ProductDetailVO vo = ProductDetailVO.from(product);
            // 写入 Redis 缓存，使用防雪崩的随机 TTL
            long ttl = randomDetailTtlMillis();
            safeSetCache(cacheKey, vo, ttl);
            log.info("预热商品缓存成功: productId={}, title={}, ttl={}ms", product.getId(), product.getTitle(), ttl);
        }
        log.info("热门商品缓存预热完成，共预热 {} 条数据", hotProducts.size());
    }

    // ==================== 既有内部接口（保持不变） ====================

    @Override
    public PageResult<ProductListVO> listProducts(String category, Integer pageNum, Integer pageSize) {
        int pageNo = PageParamUtil.normalizePageNum(pageNum);
        int size = PageParamUtil.normalizePageSize(pageSize);
        ensureTutorialNotInEs();
        Page<Product> page = new Page<>(pageNo, size);
        lambdaQuery()
                .eq(Product::getStatus, 1)
                .gt(Product::getStock, 0)
                .ne(Product::getIsTutorial, 1)
                .eq(StringUtils.hasText(category), Product::getCategory, category)
                .orderByDesc(Product::getCreateTime)
                .page(page);
        List<ProductListVO> list = page.getRecords().stream()
                .map(ProductListVO::from).collect(Collectors.toList());
        return PageResult.of(page.getTotal(), pageNo, size, list);
    }

    @Override
    public PageResult<ProductListVO> myProducts(Long sellerId, Integer pageNum, Integer pageSize) {
        int pageNo = PageParamUtil.normalizePageNum(pageNum);
        int size = PageParamUtil.normalizePageSize(pageSize);
        Page<Product> page = new Page<>(pageNo, size);
        lambdaQuery()
                .eq(Product::getSellerId, sellerId)
                .orderByDesc(Product::getCreateTime)
                .page(page);
        List<ProductListVO> list = page.getRecords().stream()
                .map(ProductListVO::from).collect(Collectors.toList());
        return PageResult.of(page.getTotal(), pageNo, size, list);
    }

    @Override
    public Product innerGetProduct(Long productId) {
        Product product = getById(productId);
        if (product == null) throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        return product;
    }

    @Override
    public ProductDetailVO getTutorialProduct() {
        Product product = lambdaQuery()
                .eq(Product::getIsTutorial, 1)
                .eq(Product::getStatus, 1)
                .orderByAsc(Product::getId)
                .last("LIMIT 1")
                .one();
        if (product == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "新手教程商品未配置");
        }
        return getDetail(product.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deductStock(Long productId, String orderNo, boolean preserveSeckillCache) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "扣减库存必须提供订单号");
        }
        StockDeductionLogEntity deductionLog = new StockDeductionLogEntity();
        deductionLog.setProductId(productId);
        deductionLog.setOrderNo(orderNo.trim());
        deductionLog.setCreateTime(LocalDateTime.now());
        try {
            stockDeductionLogMapper.insert(deductionLog);
        } catch (DuplicateKeyException e) {
            log.info("检测到重复的库存扣减请求，productId={}, orderNo={}", productId, orderNo);
            return true;
        }
        int updated = baseMapper.deductStock(productId);
        if (updated == 0) {
            Product product = getById(productId);
            if (product == null) throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
            if (product.getStatus() != 1) throw new BizException(ResultCode.PRODUCT_OFF_SHELF);
            if (product.getStock() <= 0) throw new BizException(ResultCode.PRODUCT_STOCK_INSUFFICIENT);
            throw new BizException(ResultCode.PRODUCT_STOCK_INSUFFICIENT);
        }

        // 扣减库存成功，清除详情缓存并双写 ES。
        // 秒杀消费者已在 Redis 预扣库存，消费阶段扣 DB 时不能删掉 seckill:stock，
        // 否则后续请求会按剩余 DB 库存重新预热，造成超量排队。
        evictDetailCache(productId);
        if (!preserveSeckillCache) {
            evictSeckillCache(productId);
        }
        Product updatedProduct = getById(productId);
        if (updatedProduct != null) {
            syncToEs(updatedProduct);
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreStock(Long productId, String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "库存恢复必须提供订单号");
        }
        long deductionCount = stockDeductionLogMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StockDeductionLogEntity>()
                        .eq(StockDeductionLogEntity::getProductId, productId)
                        .eq(StockDeductionLogEntity::getOrderNo, orderNo.trim()));
        if (deductionCount == 0) {
            throw new BizException(409, "对应的库存扣减尚未提交，请稍后重试");
        }
        StockRestoreLogEntity restoreLog = new StockRestoreLogEntity();
        restoreLog.setProductId(productId);
        restoreLog.setOrderNo(orderNo.trim());
        restoreLog.setCreateTime(LocalDateTime.now());
        try {
            stockRestoreLogMapper.insert(restoreLog);
        } catch (DuplicateKeyException e) {
            log.info("检测到重复的库存回滚请求，productId={}, orderNo={}, 直接返回", productId, orderNo);
            return;
        }

        Product product = getById(productId);
        if (product == null) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }

        // 已下架商品 (status == 0) 只加库存，不重新上架 (不设 status=1)
        if (product.getStatus() == 0) {
            baseMapper.restoreStock(productId);
            evictDetailCache(productId);
            evictSeckillCache(productId);
            Product updatedProduct = getById(productId);
            if (updatedProduct != null) {
                syncToEs(updatedProduct);
            }
            return;
        }

        // 正常商品，恢复库存并恢复为在售状态 (status = 1)
        baseMapper.restoreStock(productId);
        lambdaUpdate().eq(Product::getId, productId).set(Product::getStatus, 1).update();

        evictDetailCache(productId);
        evictSeckillCache(productId);
        Product updatedProduct = getById(productId);
        if (updatedProduct != null) {
            syncToEs(updatedProduct);
        }
    }

    private void enrichSellerNickname(ProductDetailVO vo) {
        if (vo == null || vo.getSellerId() == null) {
            return;
        }
        boolean needNickname = !StringUtils.hasText(vo.getSellerNickname());
        try {
            var result = userFeignClient.batchGetUsers(String.valueOf(vo.getSellerId()));
            if (result == null
                    || result.getCode() == null
                    || result.getCode() != ResultCode.SUCCESS.getCode()
                    || result.getData() == null
                    || result.getData().isEmpty()) {
                return;
            }
            result.getData().stream()
                    .filter(user -> vo.getSellerId().equals(user.getUserId()))
                    .findFirst()
                    .ifPresent(user -> {
                        if (needNickname && StringUtils.hasText(user.getNickname())) {
                            vo.setSellerNickname(user.getNickname());
                        }
                        // 评分每次强制刷新，避免详情缓存冻住信誉
                        vo.setSellerAvgRating(user.getAvgRating());
                        vo.setSellerReviewCount(user.getReviewCount() == null ? 0 : user.getReviewCount());
                    });
        } catch (Exception e) {
            log.warn("补全商品卖家信息失败，降级返回基础商品信息. productId={}, sellerId={}",
                    vo.getProductId(), vo.getSellerId(), e);
        }
    }

    private Product getAndCheckOwner(Long sellerId, Long productId) {
        Product product = getById(productId);
        if (product == null) throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        if (!product.getSellerId().equals(sellerId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return product;
    }

    // ==================== Redis 辅助（统一降级：Redis 不可用打 warn 不阻断主流程） ====================

    private boolean isRedisHealthy() {
        return System.currentTimeMillis() - lastRedisErrorTime > REDIS_COOLDOWN_MS;
    }

    private Long parseProductIdFromKey(String key) {
        try {
            if (key != null && key.startsWith("product:detail:")) {
                return Long.parseLong(key.substring("product:detail:".length()));
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Object safeGetCache(String key) {
        Long productId = parseProductIdFromKey(key);
        if (!isRedisHealthy() && productId != null) {
            LocalCacheEntry entry = localCache.get(productId);
            if (entry != null && !entry.isExpired()) {
                return entry.notFound ? new NullValueMarker() : entry.data;
            }
            return null;
        }

        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            lastRedisErrorTime = System.currentTimeMillis();
            log.warn("读取 Redis 缓存失败，降级直查 DB，key={}", key, e);
            if (productId != null) {
                LocalCacheEntry entry = localCache.get(productId);
                if (entry != null && !entry.isExpired()) {
                    return entry.notFound ? new NullValueMarker() : entry.data;
                }
            }
            return null;
        }
    }

    private void safeSetCache(String key, Object value, long ttlMillis) {
        if (!isRedisHealthy()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, value, ttlMillis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            lastRedisErrorTime = System.currentTimeMillis();
            log.warn("写入 Redis 缓存失败，已忽略，key={}", key, e);
        }
    }

    private void evictDetailCache(Long productId) {
        localCache.remove(productId);
        try {
            redisTemplate.delete(ProductCacheConstants.detailKey(productId));
        } catch (Exception e) {
            lastRedisErrorTime = System.currentTimeMillis();
            log.warn("删除 Redis 商品详情缓存失败，已忽略，productId={}", productId, e);
        }
    }

    private void evictSeckillCache(Long productId) {
        try {
            redisTemplate.delete("seckill:stock:" + productId);
            redisTemplate.delete("seckill:product:" + productId);
        } catch (Exception e) {
            lastRedisErrorTime = System.currentTimeMillis();
            log.warn("删除 Redis 秒杀缓存失败，已忽略，productId={}", productId, e);
        }
    }

    /** SETNX 抢锁：仅当 key 不存在时设置成功并返回 true */
    private boolean tryLock(String lockKey) {
        if (!isRedisHealthy()) {
            return false;
        }
        try {
            Boolean ok = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", ProductCacheConstants.LOCK_TTL.toMillis(), TimeUnit.MILLISECONDS);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            lastRedisErrorTime = System.currentTimeMillis();
            log.warn("获取重建锁失败，key={}", lockKey, e);
            return false;
        }
    }

    private void unlock(String lockKey) {
        if (!isRedisHealthy()) {
            return;
        }
        try {
            redisTemplate.delete(lockKey);
        } catch (Exception e) {
            lastRedisErrorTime = System.currentTimeMillis();
            log.warn("释放重建锁失败，key={}", lockKey, e);
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 基础 TTL + 随机 0~上限，单位毫秒 */
    private long randomDetailTtlMillis() {
        long base = ProductCacheConstants.DETAIL_TTL_BASE.toMillis();
        long randomMax = ProductCacheConstants.DETAIL_TTL_RANDOM_MAX.toMillis();
        return base + ThreadLocalRandom.current().nextLong(randomMax + 1);
    }

    // ==================== ES 辅助（统一降级：ES 不可用打 warn 不阻断主流程） ====================

    private void syncToEs(Product product) {
        if (product == null || product.getId() == null) {
            return;
        }
        // 教程商品、非在售或已售罄：从搜索索引移除
        if ((product.getIsTutorial() != null && product.getIsTutorial() == 1)
                || product.getStatus() == null || product.getStatus() != 1
                || product.getStock() == null || product.getStock() <= 0) {
            deleteFromEs(product.getId());
            return;
        }
        saveToEs(product);
    }

    private volatile boolean tutorialEsCleaned = false;

    /** 将已存在于 ES 的教程商品剔除（旧索引可能无 isTutorial 字段） */
    private void ensureTutorialNotInEs() {
        if (tutorialEsCleaned) {
            return;
        }
        synchronized (this) {
            if (tutorialEsCleaned) {
                return;
            }
            try {
                List<Product> tutorials = lambdaQuery()
                        .eq(Product::getIsTutorial, 1)
                        .select(Product::getId)
                        .list();
                for (Product p : tutorials) {
                    if (p.getId() != null) {
                        deleteFromEs(p.getId());
                    }
                }
            } catch (Exception e) {
                log.warn("清理教程商品 ES 索引失败，已忽略", e);
            }
            tutorialEsCleaned = true;
        }
    }

    private void saveToEs(Product product) {
        try {
            productRepository.save(ProductDocument.from(product));
        } catch (Exception e) {
            log.warn("ES 写入失败，已忽略（以 MySQL 为准），productId={}", product.getId(), e);
        }
    }

    private void deleteFromEs(Long productId) {
        try {
            productRepository.deleteById(productId);
        } catch (Exception e) {
            log.warn("ES 删除失败，已忽略（以 MySQL 为准），productId={}", productId, e);
        }
    }

    // ==================== 发布配额与商家统计 ====================

    @Override
    public PublishQuotaVO getPublishQuota(Long sellerId) {
        int role = resolveUserRole(sellerId);
        int used = countActiveListings(sellerId);
        PublishQuotaVO vo = new PublishQuotaVO();
        vo.setRole(role);
        vo.setUsed(used);
        if (UserRole.canPublish(role)) {
            vo.setUnlimited(true);
            vo.setLimit(-1);
            vo.setRemaining(-1);
        } else {
            vo.setUnlimited(false);
            vo.setLimit(0);
            vo.setRemaining(0);
        }
        return vo;
    }

    @Override
    public SellerProductStatsView getSellerProductStats(Long sellerId) {
        requireMerchant(sellerId);
        SellerProductStatsView stats = new SellerProductStatsView();
        stats.setActiveListings(toIntCount(lambdaQuery()
                .eq(Product::getSellerId, sellerId)
                .eq(Product::getStatus, 1)
                .count()));
        stats.setSoldListings(toIntCount(lambdaQuery()
                .eq(Product::getSellerId, sellerId)
                .eq(Product::getStatus, 2)
                .count()));
        List<Product> products = lambdaQuery()
                .eq(Product::getSellerId, sellerId)
                .select(Product::getViewCount)
                .list();
        long views = 0;
        for (Product p : products) {
            if (p.getViewCount() != null) {
                views += p.getViewCount();
            }
        }
        stats.setTotalViews(views);
        return stats;
    }

    @Override
    public SellerProductDashboardView getSellerProductDashboard(Long sellerId) {
        requireMerchant(sellerId);
        SellerProductDashboardView dashboard = new SellerProductDashboardView();
        dashboard.setSummary(getSellerProductStats(sellerId));
        List<Product> products = lambdaQuery()
                .eq(Product::getSellerId, sellerId)
                .select(Product::getCategory)
                .list();
        Map<String, Integer> categoryCounts = new java.util.LinkedHashMap<>();
        for (Product product : products) {
            String category = product.getCategory();
            if (category == null || category.isBlank()) {
                category = "未分类";
            }
            categoryCounts.merge(category, 1, Integer::sum);
        }
        List<com.campus.product.dto.CategoryCountPoint> breakdown = new java.util.ArrayList<>();
        for (Map.Entry<String, Integer> entry : categoryCounts.entrySet()) {
            com.campus.product.dto.CategoryCountPoint point = new com.campus.product.dto.CategoryCountPoint();
            point.setCategory(entry.getKey());
            point.setCount(entry.getValue());
            breakdown.add(point);
        }
        dashboard.setCategoryBreakdown(breakdown);
        return dashboard;
    }

    private void requireMerchantOrAdmin(Long sellerId) {
        int role = resolveUserRole(sellerId);
        if (!UserRole.canPublish(role)) {
            throw new BizException(ResultCode.PRODUCT_PUBLISH_MERCHANT_ONLY);
        }
    }

    private int countActiveListings(Long sellerId) {
        // 统计在售（status=1）商品数，供发布配额展示
        return toIntCount(lambdaQuery()
                .eq(Product::getSellerId, sellerId)
                .eq(Product::getStatus, 1)
                .count());
    }

    private static int toIntCount(long count) {
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    private int resolveUserRole(Long userId) {
        Result<Integer> result = userFeignClient.getUserRole(userId);
        if (result == null || result.getCode() != ResultCode.SUCCESS.getCode() || result.getData() == null) {
            return UserRole.USER;
        }
        return result.getData();
    }

    private void requireMerchant(Long userId) {
        int role = resolveUserRole(userId);
        if (!UserRole.canAccessMerchantHub(role)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
    }
}
