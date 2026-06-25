package com.campus.order.feign;

import com.campus.common.result.Result;
import com.campus.order.feign.dto.ProductDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 商品服务 Feign 客户端，通过 Nacos 服务名 {@code campus-product} 调用商品内部接口。
 */
@FeignClient(name = "campus-product")
public interface ProductFeignClient {

    /** 查询商品详情（下单前校验商品状态/库存/卖家）。 */
    @GetMapping("/product/inner/{id}")
    Result<ProductDTO> getProduct(@PathVariable("id") Long id);

    /** 扣减库存（下单时调用）。 */
    @PostMapping("/product/inner/{id}/deduct")
    Result<Boolean> deductStock(@PathVariable("id") Long id);

    /** 回滚库存（下单失败/取消时调用）。 */
    @PostMapping("/product/inner/{id}/restore")
    Result<Void> restoreStock(@PathVariable("id") Long id, @RequestParam(value = "orderNo", required = false) String orderNo);
}
