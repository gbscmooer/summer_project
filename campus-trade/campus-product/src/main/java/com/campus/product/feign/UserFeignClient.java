package com.campus.product.feign;

import com.campus.common.dto.UserPermissionsVO;
import com.campus.common.result.Result;
import com.campus.product.feign.dto.PointsTransferRequest;
import com.campus.product.feign.dto.UserBriefDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 用户服务内部接口客户端，用于补全商品详情中的卖家展示信息。
 */
@FeignClient(name = "campus-user")
public interface UserFeignClient {

    @GetMapping("/user/batch")
    Result<List<UserBriefDTO>> batchGetUsers(@RequestParam("ids") String ids);

    @GetMapping("/user/internal/role")
    Result<Integer> getUserRole(@RequestParam("userId") Long userId);

    @GetMapping("/user/internal/permissions")
    Result<UserPermissionsVO> getPermissions(@RequestParam("userId") Long userId);

    /** 话题帖点赞成功后计入每日点赞进度（按帖去重）。 */
    @PostMapping("/user/internal/events/record-like")
    Result<Object> recordLike(@RequestParam("userId") Long userId, @RequestParam("postId") Long postId);

    /** 取消点赞时回退当日进度。 */
    @PostMapping("/user/internal/events/unrecord-like")
    Result<Object> unrecordLike(@RequestParam("userId") Long userId, @RequestParam("postId") Long postId);

    /** 帖子打赏：扣打赏人积分、加作者积分。token 由 InternalFeignConfig 注入。data=true 表示本次实际划转。 */
    @PostMapping("/user/internal/points/tip")
    Result<Boolean> tipPoints(@RequestBody PointsTransferRequest request);
}
