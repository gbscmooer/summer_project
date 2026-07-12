package com.campus.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.product.dto.DailyActivityPoint;
import com.campus.product.dto.UserActivityHeatmapView;
import com.campus.product.entity.Product;
import com.campus.product.entity.ProductComment;
import com.campus.product.entity.TopicPost;
import com.campus.product.mapper.ProductCommentMapper;
import com.campus.product.mapper.ProductMapper;
import com.campus.product.mapper.TopicPostMapper;
import com.campus.product.service.UserActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class UserActivityServiceImpl implements UserActivityService {

    private static final int HEATMAP_DAYS = 365;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM", Locale.CHINA);
    private static final DateTimeFormatter DAY_LABEL_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA);

    private final TopicPostMapper topicPostMapper;
    private final ProductCommentMapper productCommentMapper;
    private final ProductMapper productMapper;

    @Override
    public UserActivityHeatmapView getHeatmap(Long userId, String filter) {
        String normalized = StringUtils.hasText(filter) ? filter.trim().toLowerCase(Locale.ROOT) : "all";
        boolean includePosts = !"comments".equals(normalized);
        boolean includeComments = !"posts".equals(normalized);

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(HEATMAP_DAYS - 1L);
        LocalDateTime startDt = start.atStartOfDay();

        Map<LocalDate, int[]> counts = new TreeMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            counts.put(d, new int[]{0, 0});
        }

        if (includePosts) {
            List<TopicPost> posts = topicPostMapper.selectList(new LambdaQueryWrapper<TopicPost>()
                    .eq(TopicPost::getUserId, userId)
                    .ge(TopicPost::getCreateTime, startDt));
            for (TopicPost post : posts) {
                if (post.getCreateTime() == null) {
                    continue;
                }
                LocalDate day = post.getCreateTime().toLocalDate();
                int[] bucket = counts.get(day);
                if (bucket != null) {
                    bucket[0]++;
                }
            }

            List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                    .eq(Product::getSellerId, userId)
                    .ge(Product::getCreateTime, startDt));
            for (Product product : products) {
                if (product.getCreateTime() == null) {
                    continue;
                }
                LocalDate day = product.getCreateTime().toLocalDate();
                int[] bucket = counts.get(day);
                if (bucket != null) {
                    bucket[0]++;
                }
            }
        }

        if (includeComments) {
            List<ProductComment> comments = productCommentMapper.selectList(new LambdaQueryWrapper<ProductComment>()
                    .eq(ProductComment::getUserId, userId)
                    .ge(ProductComment::getCreateTime, startDt));
            for (ProductComment comment : comments) {
                if (comment.getCreateTime() == null) {
                    continue;
                }
                LocalDate day = comment.getCreateTime().toLocalDate();
                int[] bucket = counts.get(day);
                if (bucket != null) {
                    bucket[1]++;
                }
            }
        }

        List<DailyActivityPoint> days = new ArrayList<>(counts.size());
        long total = 0;
        LocalDate mostActiveDay = null;
        int mostActiveCount = 0;
        Map<String, Long> monthTotals = new HashMap<>();

        for (Map.Entry<LocalDate, int[]> entry : counts.entrySet()) {
            LocalDate day = entry.getKey();
            int postCount = entry.getValue()[0];
            int commentCount = entry.getValue()[1];
            int dayTotal = postCount + commentCount;
            total += dayTotal;

            DailyActivityPoint point = new DailyActivityPoint();
            point.setDate(day.format(DATE_FMT));
            point.setPostCount(postCount);
            point.setCommentCount(commentCount);
            point.setCount(dayTotal);
            days.add(point);

            if (dayTotal > mostActiveCount) {
                mostActiveCount = dayTotal;
                mostActiveDay = day;
            }
            if (dayTotal > 0) {
                String monthKey = day.format(MONTH_FMT);
                monthTotals.merge(monthKey, (long) dayTotal, Long::sum);
            }
        }

        UserActivityHeatmapView view = new UserActivityHeatmapView();
        view.setTotalCount(total);
        view.setDays(days);
        view.setMostActiveDay(mostActiveDay != null && mostActiveCount > 0
                ? mostActiveDay.format(DAY_LABEL_FMT) : null);
        view.setMostActiveMonth(resolveMostActiveMonth(monthTotals));
        view.setLongestStreak(computeLongestStreak(counts));
        view.setCurrentStreak(computeCurrentStreak(counts, end));
        return view;
    }

    private String resolveMostActiveMonth(Map<String, Long> monthTotals) {
        String best = null;
        long bestCount = 0;
        for (Map.Entry<String, Long> entry : monthTotals.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    private int computeLongestStreak(Map<LocalDate, int[]> counts) {
        int longest = 0;
        int current = 0;
        for (int[] bucket : counts.values()) {
            if (bucket[0] + bucket[1] > 0) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 0;
            }
        }
        return longest;
    }

    private int computeCurrentStreak(Map<LocalDate, int[]> counts, LocalDate end) {
        int streak = 0;
        for (LocalDate d = end; ; d = d.minusDays(1)) {
            int[] bucket = counts.get(d);
            if (bucket == null || bucket[0] + bucket[1] <= 0) {
                break;
            }
            streak++;
            if (d.equals(counts.keySet().iterator().next())) {
                break;
            }
        }
        return streak;
    }
}
