package com.campus.order.feign;

import com.campus.common.result.Result;
import com.campus.order.feign.dto.UserBriefDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 用户服务 Feign 客户端，通过 Nacos 服务名 {@code campus-user} 调用。
 */
@FeignClient(name = "campus-user")
public interface UserFeignClient {

    /**
     * 批量查询用户简要信息。
     *
     * @param ids 逗号分隔的用户 ID，例如 "1,2"
     */
    @GetMapping("/user/batch")
    Result<List<UserBriefDTO>> batchGetUsers(@RequestParam("ids") String ids);
}
