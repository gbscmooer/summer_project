package com.campus.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.constant.UserStatus;
import com.campus.common.exception.BizException;
import com.campus.common.result.PageResult;
import com.campus.common.result.ResultCode;
import com.campus.common.util.PageParamUtil;
import com.campus.user.dto.CategoryAmount;
import com.campus.user.dto.DailyPointPoint;
import com.campus.user.dto.EventStatusVO;
import com.campus.user.dto.PointLedgerVO;
import com.campus.user.dto.PointsBalanceVO;
import com.campus.user.dto.PointsStatsView;
import com.campus.user.dto.PointsTransferRequest;
import com.campus.user.entity.DailyCheckin;
import com.campus.user.entity.DailyLikeQuest;
import com.campus.user.entity.DailyLikeQuestItem;
import com.campus.user.entity.PointLedger;
import com.campus.user.entity.User;
import com.campus.user.mapper.DailyCheckinMapper;
import com.campus.user.mapper.DailyLikeQuestItemMapper;
import com.campus.user.mapper.DailyLikeQuestMapper;
import com.campus.user.mapper.PointLedgerMapper;
import com.campus.user.mapper.UserMapper;
import com.campus.user.service.PointsConstants;
import com.campus.user.service.PointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PointsServiceImpl implements PointsService {

    private final UserMapper userMapper;
    private final PointLedgerMapper pointLedgerMapper;
    private final DailyCheckinMapper dailyCheckinMapper;
    private final DailyLikeQuestMapper dailyLikeQuestMapper;
    private final DailyLikeQuestItemMapper dailyLikeQuestItemMapper;

    @Override
    public PointsBalanceVO getBalance(Long userId) {
        User user = requireUser(userId);
        return toBalance(user);
    }

    @Override
    public EventStatusVO getEventStatus(Long userId) {
        User user = requireUser(userId);
        LocalDate today = LocalDate.now();
        boolean checkedIn = dailyCheckinMapper.selectOne(new LambdaQueryWrapper<DailyCheckin>()
                .eq(DailyCheckin::getUserId, userId)
                .eq(DailyCheckin::getCheckinDate, today)) != null;
        DailyLikeQuest quest = findQuest(userId, today);
        int likeCount = quest == null || quest.getLikeCount() == null ? 0 : quest.getLikeCount();
        boolean rewarded = quest != null && quest.getRewarded() != null && quest.getRewarded() == 1;

        EventStatusVO vo = new EventStatusVO();
        vo.setCheckedInToday(checkedIn);
        vo.setLikeCountToday(likeCount);
        vo.setLikeTarget(PointsConstants.LIKE_QUEST_TARGET);
        vo.setLikeRewardClaimed(rewarded);
        vo.setLikeRewardClaimable(likeCount >= PointsConstants.LIKE_QUEST_TARGET && !rewarded);
        vo.setCheckinPoints(PointsConstants.CHECKIN_REWARD);
        vo.setLikeRewardPoints(PointsConstants.LIKE_QUEST_REWARD);
        vo.setPoints(user.getPoints() == null ? 0 : user.getPoints());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PointsBalanceVO checkin(Long userId) {
        User user = requireUser(userId);
        LocalDate today = LocalDate.now();
        DailyCheckin existing = dailyCheckinMapper.selectOne(new LambdaQueryWrapper<DailyCheckin>()
                .eq(DailyCheckin::getUserId, userId)
                .eq(DailyCheckin::getCheckinDate, today));
        if (existing != null) {
            throw new BizException(ResultCode.ALREADY_CHECKED_IN);
        }

        DailyCheckin checkin = new DailyCheckin();
        checkin.setUserId(userId);
        checkin.setCheckinDate(today);
        checkin.setPointsAwarded(PointsConstants.CHECKIN_REWARD);
        try {
            dailyCheckinMapper.insert(checkin);
        } catch (DuplicateKeyException e) {
            throw new BizException(ResultCode.ALREADY_CHECKED_IN);
        }

        int balance = credit(user, PointsConstants.CHECKIN_REWARD,
                PointsConstants.REASON_CHECKIN,
                PointsConstants.REF_CHECKIN,
                today.toString());
        user.setPoints(balance);
        return toBalance(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EventStatusVO recordLike(Long userId, Long postId) {
        requireUser(userId);
        if (postId == null || postId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        LocalDate today = LocalDate.now();
        DailyLikeQuestItem item = new DailyLikeQuestItem();
        item.setUserId(userId);
        item.setQuestDate(today);
        item.setPostId(postId);
        try {
            dailyLikeQuestItemMapper.insert(item);
        } catch (DuplicateKeyException e) {
            // 同一帖同一天只计一次，防止取消后重赞刷任务
            return getEventStatus(userId);
        }

        DailyLikeQuest quest = findOrCreateQuest(userId, today);
        int count = quest.getLikeCount() == null ? 0 : quest.getLikeCount();
        count = count + 1;
        quest.setLikeCount(count);
        dailyLikeQuestMapper.updateById(quest);

        if (count >= PointsConstants.LIKE_QUEST_TARGET
                && (quest.getRewarded() == null || quest.getRewarded() == 0)) {
            tryAwardLikeReward(userId, today, quest);
        }
        return getEventStatus(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EventStatusVO unrecordLike(Long userId, Long postId) {
        requireUser(userId);
        if (postId == null || postId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        LocalDate today = LocalDate.now();
        DailyLikeQuest quest = findQuest(userId, today);
        if (quest != null && quest.getRewarded() != null && quest.getRewarded() == 1) {
            // 已领奖不回退，避免领奖后取消点赞再刷
            return getEventStatus(userId);
        }

        int deleted = dailyLikeQuestItemMapper.delete(new LambdaQueryWrapper<DailyLikeQuestItem>()
                .eq(DailyLikeQuestItem::getUserId, userId)
                .eq(DailyLikeQuestItem::getQuestDate, today)
                .eq(DailyLikeQuestItem::getPostId, postId));
        if (deleted <= 0 || quest == null) {
            return getEventStatus(userId);
        }

        int count = quest.getLikeCount() == null ? 0 : quest.getLikeCount();
        quest.setLikeCount(Math.max(0, count - 1));
        dailyLikeQuestMapper.updateById(quest);
        return getEventStatus(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PointsBalanceVO claimLikeReward(Long userId) {
        requireUser(userId);
        LocalDate today = LocalDate.now();
        DailyLikeQuest quest = findQuest(userId, today);
        if (quest == null || quest.getLikeCount() == null
                || quest.getLikeCount() < PointsConstants.LIKE_QUEST_TARGET) {
            throw new BizException(ResultCode.LIKE_REWARD_NOT_READY);
        }
        if (quest.getRewarded() != null && quest.getRewarded() == 1) {
            throw new BizException(ResultCode.LIKE_REWARD_ALREADY_CLAIMED);
        }
        tryAwardLikeReward(userId, today, quest);
        return getBalance(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferForOrder(PointsTransferRequest request) {
        if (request == null
                || request.getFromUserId() == null
                || request.getToUserId() == null
                || request.getAmount() == null
                || request.getAmount() <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        if (request.getFromUserId().equals(request.getToUserId())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "买卖家不能相同");
        }

        String refType = StringUtils.hasText(request.getRefType())
                ? request.getRefType() : PointsConstants.REF_ORDER;
        String refId = request.getRefId();
        if (!StringUtils.hasText(refId)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "refId 不能为空");
        }

        // 幂等：买家扣款流水已存在则视为已处理
        PointLedger existing = pointLedgerMapper.selectOne(new LambdaQueryWrapper<PointLedger>()
                .eq(PointLedger::getUserId, request.getFromUserId())
                .eq(PointLedger::getRefType, refType)
                .eq(PointLedger::getRefId, refId)
                .eq(PointLedger::getReason, PointsConstants.REASON_ORDER_PAY_DEBIT)
                .last("LIMIT 1"));
        if (existing != null) {
            return;
        }

        User buyer = requireUser(request.getFromUserId());
        User seller = requireUser(request.getToUserId());
        int amount = request.getAmount();
        int buyerPoints = buyer.getPoints() == null ? 0 : buyer.getPoints();
        if (buyerPoints < amount) {
            throw new BizException(ResultCode.POINTS_INSUFFICIENT);
        }

        boolean deducted = userMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>()
                        .eq(User::getId, buyer.getId())
                        .ge(User::getPoints, amount)
                        .setSql("points = points - " + amount)) > 0;
        if (!deducted) {
            throw new BizException(ResultCode.POINTS_INSUFFICIENT);
        }
        User buyerAfter = userMapper.selectById(buyer.getId());
        writeLedger(buyer.getId(), -amount, buyerAfter.getPoints(),
                PointsConstants.REASON_ORDER_PAY_DEBIT, refType, refId);

        userMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>()
                        .eq(User::getId, seller.getId())
                        .setSql("points = points + " + amount));
        User sellerAfter = userMapper.selectById(seller.getId());
        writeLedger(seller.getId(), amount, sellerAfter.getPoints(),
                PointsConstants.REASON_ORDER_PAY_CREDIT, refType, refId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean transferTip(PointsTransferRequest request) {
        if (request == null
                || request.getFromUserId() == null
                || request.getToUserId() == null
                || request.getAmount() == null
                || request.getAmount() <= 0) {
            throw new BizException(ResultCode.TOPIC_TIP_INVALID);
        }
        if (request.getFromUserId().equals(request.getToUserId())) {
            throw new BizException(ResultCode.TOPIC_TIP_SELF);
        }

        String refType = StringUtils.hasText(request.getRefType())
                ? request.getRefType() : PointsConstants.REF_TOPIC_TIP;
        String refId = request.getRefId();
        if (!StringUtils.hasText(refId)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "refId 不能为空");
        }

        // 幂等：打赏人扣款流水已存在则视为已处理
        PointLedger existing = pointLedgerMapper.selectOne(new LambdaQueryWrapper<PointLedger>()
                .eq(PointLedger::getUserId, request.getFromUserId())
                .eq(PointLedger::getRefType, refType)
                .eq(PointLedger::getRefId, refId)
                .eq(PointLedger::getReason, PointsConstants.REASON_TOPIC_TIP_DEBIT)
                .last("LIMIT 1"));
        if (existing != null) {
            return false;
        }

        User from = requireUser(request.getFromUserId());
        User to = requireUser(request.getToUserId());
        int amount = request.getAmount();
        int fromPoints = from.getPoints() == null ? 0 : from.getPoints();
        if (fromPoints < amount) {
            throw new BizException(ResultCode.POINTS_INSUFFICIENT);
        }

        boolean deducted = userMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>()
                        .eq(User::getId, from.getId())
                        .ge(User::getPoints, amount)
                        .setSql("points = points - " + amount)) > 0;
        if (!deducted) {
            throw new BizException(ResultCode.POINTS_INSUFFICIENT);
        }
        User fromAfter = userMapper.selectById(from.getId());
        writeLedger(from.getId(), -amount, fromAfter.getPoints(),
                PointsConstants.REASON_TOPIC_TIP_DEBIT, refType, refId);

        userMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>()
                        .eq(User::getId, to.getId())
                        .setSql("points = points + " + amount));
        User toAfter = userMapper.selectById(to.getId());
        writeLedger(to.getId(), amount, toAfter.getPoints(),
                PointsConstants.REASON_TOPIC_TIP_CREDIT, refType, refId);
        return true;
    }

    @Override
    public PageResult<PointLedgerVO> listLedger(Long userId, Integer pageNum, Integer pageSize, String direction) {
        requireUser(userId);
        int pageNo = PageParamUtil.normalizePageNum(pageNum);
        int size = PageParamUtil.normalizePageSize(pageSize);
        Page<PointLedger> page = new Page<>(pageNo, size);
        LambdaQueryWrapper<PointLedger> query = new LambdaQueryWrapper<PointLedger>()
                .eq(PointLedger::getUserId, userId);
        if ("earn".equalsIgnoreCase(direction)) {
            query.gt(PointLedger::getDelta, 0);
        } else if ("spend".equalsIgnoreCase(direction)) {
            query.lt(PointLedger::getDelta, 0);
        }
        query.orderByDesc(PointLedger::getCreateTime).orderByDesc(PointLedger::getId);
        pointLedgerMapper.selectPage(page, query);
        List<PointLedgerVO> list = page.getRecords().stream()
                .map(this::toLedgerVO)
                .collect(Collectors.toList());
        return PageResult.of(page.getTotal(), pageNo, size, list);
    }

    @Override
    public PointsStatsView getStats(Long userId, int days) {
        User user = requireUser(userId);
        int rangeDays = Math.min(90, Math.max(1, days));
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(rangeDays - 1L);
        LocalDateTime startTime = startDate.atStartOfDay();

        List<PointLedger> ledgers = pointLedgerMapper.selectList(new LambdaQueryWrapper<PointLedger>()
                .eq(PointLedger::getUserId, userId)
                .ge(PointLedger::getCreateTime, startTime)
                .orderByAsc(PointLedger::getCreateTime));

        int totalSpent = 0;
        int spentProducts = 0;
        int spentTips = 0;
        int totalEarned = 0;
        int earnedCheckin = 0;
        int earnedLike = 0;
        int earnedSales = 0;
        int earnedTips = 0;
        Map<LocalDate, DailyPointPoint> dailyMap = new HashMap<>();

        for (PointLedger ledger : ledgers) {
            int delta = ledger.getDelta() == null ? 0 : ledger.getDelta();
            String reason = ledger.getReason();
            LocalDate day = ledger.getCreateTime() == null
                    ? today
                    : ledger.getCreateTime().toLocalDate();
            DailyPointPoint dayPoint = dailyMap.computeIfAbsent(day, d -> newEmptyDay(d));

            if (delta > 0) {
                totalEarned += delta;
                dayPoint.setEarned(dayPoint.getEarned() + delta);
                if (PointsConstants.REASON_CHECKIN.equals(reason)) {
                    earnedCheckin += delta;
                    dayPoint.setEarnedCheckin(dayPoint.getEarnedCheckin() + delta);
                } else if (PointsConstants.REASON_LIKE_REWARD.equals(reason)) {
                    earnedLike += delta;
                    dayPoint.setEarnedLike(dayPoint.getEarnedLike() + delta);
                } else if (PointsConstants.REASON_ORDER_PAY_CREDIT.equals(reason)) {
                    earnedSales += delta;
                    dayPoint.setEarnedSales(dayPoint.getEarnedSales() + delta);
                } else if (PointsConstants.REASON_TOPIC_TIP_CREDIT.equals(reason)) {
                    earnedTips += delta;
                    dayPoint.setEarnedTips(dayPoint.getEarnedTips() + delta);
                }
            } else if (delta < 0) {
                int abs = -delta;
                totalSpent += abs;
                if (PointsConstants.REASON_ORDER_PAY_DEBIT.equals(reason)) {
                    spentProducts += abs;
                    dayPoint.setSpentProducts(dayPoint.getSpentProducts() + abs);
                } else if (PointsConstants.REASON_TOPIC_TIP_DEBIT.equals(reason)) {
                    spentTips += abs;
                    dayPoint.setSpentTips(dayPoint.getSpentTips() + abs);
                }
            }
        }

        int spentOther = Math.max(0, totalSpent - spentProducts - spentTips);
        int earnedOther = Math.max(0, totalEarned - earnedCheckin - earnedLike - earnedSales - earnedTips);

        PointsStatsView view = new PointsStatsView();
        view.setPoints(user.getPoints() == null ? 0 : user.getPoints());
        view.setTotalSpent(totalSpent);
        view.setSpentProducts(spentProducts);
        view.setSpentTips(spentTips);
        view.setTotalEarned(totalEarned);
        view.setEarnedCheckin(earnedCheckin);
        view.setEarnedLike(earnedLike);
        view.setEarnedSales(earnedSales);
        view.setEarnedTips(earnedTips);
        view.setPieSpend(List.of(
                new CategoryAmount("products", spentProducts),
                new CategoryAmount("tips", spentTips),
                new CategoryAmount("other", spentOther)
        ));
        view.setPieEarn(List.of(
                new CategoryAmount("checkin", earnedCheckin),
                new CategoryAmount("like", earnedLike),
                new CategoryAmount("sales", earnedSales),
                new CategoryAmount("tips", earnedTips),
                new CategoryAmount("other", earnedOther)
        ));

        List<DailyPointPoint> daily = new ArrayList<>(rangeDays);
        for (LocalDate d = startDate; !d.isAfter(today); d = d.plusDays(1)) {
            DailyPointPoint point = dailyMap.get(d);
            if (point == null) {
                point = newEmptyDay(d);
            }
            daily.add(point);
        }
        view.setDaily(daily);
        return view;
    }

    private static DailyPointPoint newEmptyDay(LocalDate d) {
        DailyPointPoint p = new DailyPointPoint();
        p.setDate(d.toString());
        p.setSpentProducts(0);
        p.setSpentTips(0);
        p.setEarned(0);
        p.setEarnedCheckin(0);
        p.setEarnedLike(0);
        p.setEarnedSales(0);
        p.setEarnedTips(0);
        return p;
    }

    private PointLedgerVO toLedgerVO(PointLedger ledger) {
        PointLedgerVO vo = new PointLedgerVO();
        vo.setId(ledger.getId());
        vo.setDelta(ledger.getDelta());
        vo.setBalanceAfter(ledger.getBalanceAfter());
        vo.setReason(ledger.getReason());
        vo.setReasonLabel(reasonLabel(ledger.getReason()));
        vo.setRefType(ledger.getRefType());
        vo.setRefId(ledger.getRefId());
        vo.setCreateTime(ledger.getCreateTime());
        vo.setCategory(ledgerCategory(ledger.getDelta(), ledger.getReason()));
        return vo;
    }

    private static String reasonLabel(String reason) {
        if (reason == null) {
            return "";
        }
        return switch (reason) {
            case PointsConstants.REASON_CHECKIN -> "每日签到";
            case PointsConstants.REASON_LIKE_REWARD -> "点赞任务奖励";
            case PointsConstants.REASON_ORDER_PAY_DEBIT -> "购买商品";
            case PointsConstants.REASON_ORDER_PAY_CREDIT -> "商品销售收入";
            case PointsConstants.REASON_TOPIC_TIP_DEBIT -> "打赏帖子";
            case PointsConstants.REASON_TOPIC_TIP_CREDIT -> "收到打赏";
            default -> reason;
        };
    }

    private static String ledgerCategory(Integer delta, String reason) {
        int d = delta == null ? 0 : delta;
        if (d > 0) {
            return "earned";
        }
        if (PointsConstants.REASON_ORDER_PAY_DEBIT.equals(reason)) {
            return "product";
        }
        if (PointsConstants.REASON_TOPIC_TIP_DEBIT.equals(reason)) {
            return "tip";
        }
        return "other";
    }

    private void tryAwardLikeReward(Long userId, LocalDate today, DailyLikeQuest quest) {
        boolean marked = dailyLikeQuestMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<DailyLikeQuest>()
                        .eq(DailyLikeQuest::getId, quest.getId())
                        .eq(DailyLikeQuest::getRewarded, 0)
                        .set(DailyLikeQuest::getRewarded, 1)) > 0;
        if (!marked) {
            return;
        }
        User user = requireUser(userId);
        credit(user, PointsConstants.LIKE_QUEST_REWARD,
                PointsConstants.REASON_LIKE_REWARD,
                PointsConstants.REF_LIKE_QUEST,
                today.toString());
        quest.setRewarded(1);
    }

    private int credit(User user, int delta, String reason, String refType, String refId) {
        int current = user.getPoints() == null ? 0 : user.getPoints();
        int after = current + delta;
        userMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>()
                        .eq(User::getId, user.getId())
                        .setSql("points = points + " + delta));
        User refreshed = userMapper.selectById(user.getId());
        int balance = refreshed.getPoints() == null ? after : refreshed.getPoints();
        writeLedger(user.getId(), delta, balance, reason, refType, refId);
        return balance;
    }

    private void writeLedger(Long userId, int delta, int balanceAfter,
                             String reason, String refType, String refId) {
        PointLedger ledger = new PointLedger();
        ledger.setUserId(userId);
        ledger.setDelta(delta);
        ledger.setBalanceAfter(balanceAfter);
        ledger.setReason(reason);
        ledger.setRefType(refType);
        ledger.setRefId(refId);
        pointLedgerMapper.insert(ledger);
    }

    private DailyLikeQuest findQuest(Long userId, LocalDate date) {
        return dailyLikeQuestMapper.selectOne(new LambdaQueryWrapper<DailyLikeQuest>()
                .eq(DailyLikeQuest::getUserId, userId)
                .eq(DailyLikeQuest::getQuestDate, date));
    }

    private DailyLikeQuest findOrCreateQuest(Long userId, LocalDate date) {
        DailyLikeQuest quest = findQuest(userId, date);
        if (quest != null) {
            return quest;
        }
        quest = new DailyLikeQuest();
        quest.setUserId(userId);
        quest.setQuestDate(date);
        quest.setLikeCount(0);
        quest.setRewarded(0);
        try {
            dailyLikeQuestMapper.insert(quest);
        } catch (DuplicateKeyException e) {
            quest = findQuest(userId, date);
            if (quest == null) {
                throw e;
            }
        }
        return quest;
    }

    private User requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (UserStatus.isEffectivelyBanned(user.getStatus(), user.getBanUntil())) {
            throw new BizException(ResultCode.USER_BANNED);
        }
        if (user.getPoints() == null) {
            user.setPoints(0);
        }
        return user;
    }

    private PointsBalanceVO toBalance(User user) {
        PointsBalanceVO vo = new PointsBalanceVO();
        vo.setUserId(user.getId());
        vo.setPoints(user.getPoints() == null ? 0 : user.getPoints());
        return vo;
    }
}
