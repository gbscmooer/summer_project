package com.campus.order.controller;

import com.campus.common.result.Result;
import com.campus.order.feign.ProductFeignClient;
import com.campus.order.feign.UserFeignClient;
import com.campus.order.feign.dto.ProductDTO;
import com.campus.order.feign.dto.UserBriefDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * P4 临时验证接口：用于证明 OpenFeign 跨服务调用真实调通。
 * <p><b>P5 实现真实下单业务后删除本类。</b>
 */
@RestController
@RequiredArgsConstructor
public class FeignTestController {

    private final ProductFeignClient productFeign;
    private final UserFeignClient userFeign;

    /**
     * 同时调用商品服务（按 productId 查商品）与用户服务（批量查 id=1,2 的用户），
     * 将两者结果组装成 Map 返回，证明 Feign 调用链路打通。
     */
    @GetMapping("/order/ping/{productId}")
    public Result<Map<String, Object>> ping(@PathVariable("productId") Long productId) {
        Result<ProductDTO> productResult = productFeign.getProduct(productId);
        Result<List<UserBriefDTO>> usersResult = userFeign.batchGetUsers("1,2");

        Map<String, Object> map = new HashMap<>();
        map.put("product", productResult);
        map.put("users", usersResult);
        return Result.success(map);
    }
}
