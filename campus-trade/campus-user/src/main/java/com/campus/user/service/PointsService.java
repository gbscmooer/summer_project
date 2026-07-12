package com.campus.user.service;

import com.campus.common.result.PageResult;
import com.campus.user.dto.EventStatusVO;
import com.campus.user.dto.PointLedgerVO;
import com.campus.user.dto.PointsBalanceVO;
import com.campus.user.dto.PointsStatsView;
import com.campus.user.dto.PointsTransferRequest;

public interface PointsService {

    PointsBalanceVO getBalance(Long userId);

    EventStatusVO getEventStatus(Long userId);

    /** 注册赠送积分入账流水（幂等：同一 userId 只记一次）。 */
    void recordRegisterBonus(Long userId);

    /** 每日签到，成功返回最新余额。 */
    PointsBalanceVO checkin(Long userId);

    /**
     * 话题帖点赞成功时由 product 服务内部调用：按帖去重累计今日点赞；满 5 次自动发奖。
     */
    EventStatusVO recordLike(Long userId, Long postId);

    /**
     * 取消点赞时回退当日进度（已领奖不回退）。
     */
    EventStatusVO unrecordLike(Long userId, Long postId);

    /** 手动领取今日点赞奖励（若已自动发放则返回已领取错误）。 */
    PointsBalanceVO claimLikeReward(Long userId);

    /**
     * 订单支付：扣买家积分、加卖家积分。幂等：同一 refType+refId 只处理一次。
     */
    void transferForOrder(PointsTransferRequest request);

    /**
     * 帖子打赏：扣打赏人积分、加作者积分。幂等：fromUserId + REF_TOPIC_TIP + refId。
     *
     * @return true 表示本次实际划转；false 表示幂等命中（已处理过）
     */
    boolean transferTip(PointsTransferRequest request);

    /** direction: earn=收入(delta>0), spend=支出(delta<0), 其它/空=全部 */
    PageResult<PointLedgerVO> listLedger(Long userId, Integer pageNum, Integer pageSize, String direction);

    /** 积分统计；days 默认 7，范围 1–90。 */
    PointsStatsView getStats(Long userId, int days);
}
