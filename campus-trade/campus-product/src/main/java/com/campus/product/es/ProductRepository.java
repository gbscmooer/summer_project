package com.campus.product.es;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * 商品 ES 仓库。基本的 save/delete/findById 由 ElasticsearchRepository 提供，
 * 复杂条件检索在 service 中用 ElasticsearchOperations + CriteriaQuery 实现。
 */
public interface ProductRepository extends ElasticsearchRepository<ProductDocument, Long> {
}
