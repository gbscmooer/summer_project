package com.campus.user.controller;

import com.campus.common.exception.BizException;
import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import com.campus.common.security.InternalApiTokenValidator;
import com.campus.user.dto.EventStatusVO;
import com.campus.user.dto.PointLedgerVO;
import com.campus.user.dto.PointsBalanceVO;
import com.campus.user.dto.PointsStatsView;
import com.campus.user.dto.PointsTransferRequest;
import com.campus.user.service.PointsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class EventsController {

    private final PointsService pointsService;
    private final InternalApiTokenValidator internalApiTokenValidator;

    @GetMapping("/points")
    public Result<PointsBalanceVO> getPoints(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireLogin(userId);
        return Result.success(pointsService.getBalance(userId));
    }

    @GetMapping("/points/ledger")
    public Result<PageResult<PointLedgerVO>> listLedger(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String direction) {
        requireLogin(userId);
        return Result.success(pointsService.listLedger(userId, pageNum, pageSize, direction));
    }

    @GetMapping("/points/stats")
    public Result<PointsStatsView> getStats(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(defaultValue = "7") Integer days) {
        requireLogin(userId);
        int range = days == null ? 7 : days;
        return Result.success(pointsService.getStats(userId, range));
    }

    @GetMapping("/events/status")
    public Result<EventStatusVO> getEventStatus(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireLogin(userId);
        return Result.success(pointsService.getEventStatus(userId));
    }

    @PostMapping("/events/checkin")
    public Result<PointsBalanceVO> checkin(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireLogin(userId);
        return Result.success("签到成功", pointsService.checkin(userId));
    }

    @PostMapping("/events/claim-like-reward")
    public Result<PointsBalanceVO> claimLikeReward(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireLogin(userId);
        return Result.success("领取成功", pointsService.claimLikeReward(userId));
    }

    /** 内部：订单支付积分划转。 */
    @PostMapping("/internal/points/transfer")
    public Result<Void> transferPoints(
            @RequestHeader(value = InternalApiTokenValidator.HEADER_NAME, required = false) String internalToken,
            @Valid @RequestBody PointsTransferRequest request) {
        internalApiTokenValidator.requireValid(internalToken);
        pointsService.transferForOrder(request);
        return Result.success(null);
    }

    /** 内部：帖子打赏积分划转。data=true 表示本次实际划转。 */
    @PostMapping("/internal/points/tip")
    public Result<Boolean> tipPoints(
            @RequestHeader(value = InternalApiTokenValidator.HEADER_NAME, required = false) String internalToken,
            @Valid @RequestBody PointsTransferRequest request) {
        internalApiTokenValidator.requireValid(internalToken);
        return Result.success(pointsService.transferTip(request));
    }

    /** 内部：话题帖点赞成功计入每日点赞进度（按帖去重）。 */
    @PostMapping("/internal/events/record-like")
    public Result<EventStatusVO> recordLike(
            @RequestHeader(value = InternalApiTokenValidator.HEADER_NAME, required = false) String internalToken,
            @RequestParam Long userId,
            @RequestParam Long postId) {
        internalApiTokenValidator.requireValid(internalToken);
        return Result.success(pointsService.recordLike(userId, postId));
    }

    /** 内部：取消点赞时回退当日进度。 */
    @PostMapping("/internal/events/unrecord-like")
    public Result<EventStatusVO> unrecordLike(
            @RequestHeader(value = InternalApiTokenValidator.HEADER_NAME, required = false) String internalToken,
            @RequestParam Long userId,
            @RequestParam Long postId) {
        internalApiTokenValidator.requireValid(internalToken);
        return Result.success(pointsService.unrecordLike(userId, postId));
    }

    private void requireLogin(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
    }
}
