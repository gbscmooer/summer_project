package com.campus.product.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BizException;
import com.campus.common.result.PageResult;
import com.campus.common.result.ResultCode;
import com.campus.product.constant.ProductCacheConstants;
import com.campus.product.dto.NullValueMarker;
import com.campus.product.dto.ProductDetailVO;
import com.campus.product.dto.ProductListVO;
import com.campus.product.dto.ProductRequest;
import com.campus.product.entity.Product;
import com.campus.product.es.ProductDocument;
import com.campus.product.es.ProductRepository;
import com.campus.product.mapper.ProductMapper;
import com.campus.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
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

    private static final ZoneId ZONE = ZoneId.systemDefault();

    // ==================== 商品写操作（含 ES 双写） ====================

    @Override
    public Long publish(Long sellerId, ProductRequest request) {
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
        // 双写 ES：更新文档
        saveToEs(product);
    }

    @Override
    public void remove(Long sellerId, Long productId) {
        Product product = getAndCheckOwner(sellerId, productId);
        product.setStatus(0);
        updateById(product);
        // 下架后删缓存
        evictDetailCache(productId);
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
                // 命中也继续累加浏览量。
                // 注意：viewCount 字段是缓存写入时的快照，在缓存有效期（最长约 35 分钟）内
                // 返回值可能比真实库值轻微滞后，这是为降低 DB 压力而接受的已知取舍。
                baseMapper.incrementViewCount(productId);
                return vo;
            }
        }

        // 2. 未命中：用互斥锁重建，防止缓存击穿（热点 key 失效瞬间大量请求压垮 DB）
        return loadWithMutex(productId, cacheKey);
    }

    /**
     * 缓存击穿防护：SETNX 抢重建锁。
     * 抢到锁 → 查库回填后释放锁；没抢到 → 短暂 sleep 后重试读缓存（最多若干次），
     * 仍读不到则兜底直接查库（避免长时间阻塞）。
     */
    private ProductDetailVO loadWithMutex(Long productId, String cacheKey) {
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
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }
        baseMapper.incrementViewCount(productId);
        product.setViewCount(product.getViewCount() + 1);
        ProductDetailVO vo = ProductDetailVO.from(product);
        // 缓存雪崩防护：基础 30 分钟 + 随机 0~5 分钟，错峰过期
        safeSetCache(cacheKey, vo, randomDetailTtlMillis());
        return vo;
    }

    // ==================== Elasticsearch 搜索 ====================

    @Override
    public PageResult<ProductListVO> search(String keyword, String category,
                                            BigDecimal minPrice, BigDecimal maxPrice,
                                            String sort, Integer pageNum, Integer pageSize) {
        int page = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        int size = (pageSize == null || pageSize < 1) ? 10 : pageSize;

        // 仅查在售商品
        Criteria criteria = new Criteria("status").is(1);
        // 关键词：标题或描述命中（分词匹配）
        if (StringUtils.hasText(keyword)) {
            Criteria kw = new Criteria("title").matches(keyword)
                    .or(new Criteria("description").matches(keyword));
            criteria = criteria.and(kw);
        }
        if (StringUtils.hasText(category)) {
            criteria = criteria.and(new Criteria("category").is(category));
        }
        // 价格区间，min/max 各自可空
        if (minPrice != null) {
            criteria = criteria.and(new Criteria("price").greaterThanEqual(minPrice.doubleValue()));
        }
        if (maxPrice != null) {
            criteria = criteria.and(new Criteria("price").lessThanEqual(maxPrice.doubleValue()));
        }

        CriteriaQuery query = new CriteriaQuery(criteria);
        query.setPageable(PageRequest.of(page - 1, size));
        query.addSort(resolveSort(sort));

        SearchHits<ProductDocument> hits = elasticsearchTemplate.search(query, ProductDocument.class);
        List<ProductListVO> list = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(this::toListVO)
                .collect(Collectors.toList());
        return PageResult.of(hits.getTotalHits(), page, size, list);
    }

    /** sort 解析：price_asc/price_desc 按 price，time_desc（默认）按 createTime desc */
    private Sort resolveSort(String sort) {
        if ("price_asc".equals(sort)) {
            return Sort.by(Sort.Direction.ASC, "price");
        }
        if ("price_desc".equals(sort)) {
            return Sort.by(Sort.Direction.DESC, "price");
        }
        // time_desc 默认
        return Sort.by(Sort.Direction.DESC, "createTime");
    }

    /** ES 文档 → ProductListVO（与 /product/list 结构一致，createTime 从 epoch 毫秒转回 LocalDateTime） */
    private ProductListVO toListVO(ProductDocument doc) {
        ProductListVO vo = new ProductListVO();
        vo.setProductId(doc.getId());
        vo.setTitle(doc.getTitle());
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
        // 存量刷入：把 MySQL 中 status=1 的全部商品 bulk 写入 ES
        List<Product> products = lambdaQuery().eq(Product::getStatus, 1).list();
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
        Page<Product> page = new Page<>(pageNum, pageSize);
        lambdaQuery()
                .eq(Product::getStatus, 1)
                .eq(StringUtils.hasText(category), Product::getCategory, category)
                .orderByDesc(Product::getCreateTime)
                .page(page);
        List<ProductListVO> list = page.getRecords().stream()
                .map(ProductListVO::from).collect(Collectors.toList());
        return PageResult.of(page.getTotal(), pageNum, pageSize, list);
    }

    @Override
    public PageResult<ProductListVO> myProducts(Long sellerId, Integer pageNum, Integer pageSize) {
        Page<Product> page = new Page<>(pageNum, pageSize);
        lambdaQuery()
                .eq(Product::getSellerId, sellerId)
                .orderByDesc(Product::getCreateTime)
                .page(page);
        List<ProductListVO> list = page.getRecords().stream()
                .map(ProductListVO::from).collect(Collectors.toList());
        return PageResult.of(page.getTotal(), pageNum, pageSize, list);
    }

    @Override
    public Product innerGetProduct(Long productId) {
        Product product = getById(productId);
        if (product == null) throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        return product;
    }

    @Override
    public boolean deductStock(Long productId) {
        Product product = getById(productId);
        if (product == null) throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        if (product.getStatus() != 1) throw new BizException(ResultCode.PRODUCT_OFF_SHELF);
        if (product.getStock() <= 0) throw new BizException(ResultCode.PRODUCT_STOCK_INSUFFICIENT);
        int updated = baseMapper.deductStock(productId);
        if (updated == 0) throw new BizException(ResultCode.PRODUCT_STOCK_INSUFFICIENT);
        // 库存归零时自动标记已售
        if (product.getStock() - 1 == 0) {
            lambdaUpdate().eq(Product::getId, productId).set(Product::getStatus, 2).update();
        }
        return true;
    }

    @Override
    public void restoreStock(Long productId) {
        baseMapper.restoreStock(productId);
        lambdaUpdate().eq(Product::getId, productId).set(Product::getStatus, 1).update();
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

    private Object safeGetCache(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("读取 Redis 缓存失败，降级直查 DB，key={}", key, e);
            return null;
        }
    }

    private void safeSetCache(String key, Object value, long ttlMillis) {
        try {
            redisTemplate.opsForValue().set(key, value, ttlMillis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("写入 Redis 缓存失败，已忽略，key={}", key, e);
        }
    }

    private void evictDetailCache(Long productId) {
        try {
            redisTemplate.delete(ProductCacheConstants.detailKey(productId));
        } catch (Exception e) {
            log.warn("删除 Redis 缓存失败，已忽略，productId={}", productId, e);
        }
    }

    /** SETNX 抢锁：仅当 key 不存在时设置成功并返回 true */
    private boolean tryLock(String lockKey) {
        try {
            Boolean ok = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", ProductCacheConstants.LOCK_TTL.toMillis(), TimeUnit.MILLISECONDS);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            // Redis 异常时视作未抢到锁，由调用方走 DB 兜底
            log.warn("获取重建锁失败，key={}", lockKey, e);
            return false;
        }
    }

    private void unlock(String lockKey) {
        try {
            redisTemplate.delete(lockKey);
        } catch (Exception e) {
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
}
