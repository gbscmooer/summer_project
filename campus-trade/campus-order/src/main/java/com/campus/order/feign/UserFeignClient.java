package com.campus.order.feign;

import com.campus.common.dto.UserPermissionsVO;
import com.campus.common.result.Result;
import com.campus.order.feign.dto.ApplyRatingRequest;
import com.campus.order.feign.dto.PointsTransferRequest;
import com.campus.order.feign.dto.UserBriefDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @GetMapping("/user/internal/role")
    Result<Integer> getUserRole(@RequestParam("userId") Long userId);

    @GetMapping("/user/internal/permissions")
    Result<UserPermissionsVO> getPermissions(@RequestParam("userId") Long userId);

    @GetMapping("/user/internal/all-ids")
    Result<List<Long>> listAllUserIds();

    @GetMapping("/user/internal/resolve-usernames")
    Result<List<Long>> resolveUserIdsByUsernames(@RequestParam("usernames") String usernames);

    /** 订单支付：扣买家积分、加卖家积分。 */
    @PostMapping("/user/internal/points/transfer")
    Result<Void> transferPoints(@RequestBody PointsTransferRequest request);

    /** 订单评价后增量更新卖家 avg_rating / review_count。 */
    @PostMapping("/user/internal/rating/apply")
    Result<Void> applyRating(@RequestBody ApplyRatingRequest request);
}
