package com.campus.product.feign;

import com.campus.common.result.Result;
import com.campus.product.feign.dto.UserBriefDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 用户服务内部接口客户端，用于补全商品详情中的卖家展示信息。
 */
@FeignClient(name = "campus-user")
public interface UserFeignClient {

    @GetMapping("/user/batch")
    Result<List<UserBriefDTO>> batchGetUsers(@RequestParam("ids") String ids);
}
