package com.campus.product.ai.service.impl;

import com.campus.common.exception.BizException;
import com.campus.common.result.PageResult;
import com.campus.common.result.ResultCode;
import com.campus.product.ai.client.AiModelClient;
import com.campus.product.ai.dto.AiListingAnalysis;
import com.campus.product.ai.dto.AiListingDraftRequest;
import com.campus.product.ai.dto.AiListingDraftResponse;
import com.campus.product.ai.dto.AiSearchIntent;
import com.campus.product.ai.dto.AiSearchRequest;
import com.campus.product.ai.dto.AiSearchResponse;
import com.campus.product.ai.service.AiAssistantService;
import com.campus.product.dto.ProductListVO;
import com.campus.product.service.ProductImageService;
import com.campus.product.service.ProductService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class AiAssistantServiceImpl implements AiAssistantService {

    private static final Semaphore AI_FLOW_CONCURRENCY = new Semaphore(4, true);

    private static final Set<String> CATEGORIES = Set.of("教材", "数码", "生活", "其他");
    private static final Set<String> SORTS = Set.of("price_asc", "price_desc", "time_desc");
    private static final String SEARCH_SYSTEM_PROMPT = """
            你是校园二手交易搜索助手。把用户中文或英文购买需求转换为严格 JSON，不要输出解释或 Markdown。
            JSON 字段：keyword(适合站内标题检索的精简核心商品名), category(教材/数码/生活/其他之一或null),
            minPrice(数字或null), maxPrice(数字或null), sort(price_asc/price_desc/time_desc), explanation(一句中文意图说明)。
            不要把“我想买、需要、便宜”等泛词放进 keyword。未说明排序时用 time_desc。
            口语简称要还原成站内常见写法：高数→高等数学，线代→线性代数，四级→英语四级，电脑→笔记本或电脑。
            keyword 尽量短且能出现在商品标题里，不要塞预算、成色等筛选词。
            """;
    private static final String LISTING_SYSTEM_PROMPT = """
            你是校园二手商品发布助手。观察多张商品实拍图并结合卖家备注，输出严格 JSON，不要输出 Markdown。
            JSON 字段：title(不超过50字), description(客观描述品牌型号、外观、可见瑕疵和交付建议，不编造),
            category(教材/数码/生活/其他之一), condition(全新/几乎全新/良好/一般/较差之一),
            keywords(1到3个用于搜索同款的精简词), referencePrice(无法判断为null), confidence(0到1), warnings(字符串数组)。
            图片看不清或无法确认的信息必须放入 warnings，严禁虚构型号、版本、配件或功能状态。
            """;

    private final AiModelClient aiModelClient;
    private final ProductService productService;
    private final ProductImageService productImageService;
    private final ObjectMapper objectMapper;

    @Override
    public AiSearchResponse search(AiSearchRequest request) {
        return withAiPermit(() -> doSearch(request));
    }

    private AiSearchResponse doSearch(AiSearchRequest request) {
        AiSearchIntent intent = parse(
                aiModelClient.completeJson(SEARCH_SYSTEM_PROMPT, request.getQuery(), List.of()),
                AiSearchIntent.class
        );
        normalizeIntent(intent);
        int pageSize = request.getPageSize() == null ? 12 : request.getPageSize();
        PageResult<ProductListVO> result = productService.search(
                intent.getKeyword(), intent.getCategory(), intent.getMinPrice(), intent.getMaxPrice(),
                intent.getSort(), 1, pageSize
        );
        List<ProductListVO> products = result.getList() == null ? List.of() : result.getList();
        BigDecimal low = products.stream().map(ProductListVO::getPrice).filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);
        BigDecimal high = products.stream().map(ProductListVO::getPrice).filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        String summary = products.isEmpty()
                ? "暂时没有找到完全匹配的在售商品，可以尝试减少品牌、版本或价格限制。"
                : "已按“" + intent.getKeyword() + "”找到 " + result.getTotal() + " 件在售商品，下面优先展示最符合条件的结果。";
        return AiSearchResponse.builder()
                .query(request.getQuery())
                .summary(summary)
                .intent(intent)
                .total(result.getTotal())
                .priceLow(low)
                .priceHigh(high)
                .products(products)
                .build();
    }

    @Override
    public AiListingDraftResponse createListingDraft(Long sellerId, AiListingDraftRequest request) {
        return withAiPermit(() -> doCreateListingDraft(sellerId, request));
    }

    private AiListingDraftResponse doCreateListingDraft(Long sellerId, AiListingDraftRequest request) {
        List<String> visionImages = request.getImages().size() <= 3
                ? request.getImages()
                : request.getImages().subList(0, 3);
        List<String> dataUrls = productImageService.readAsDataUrls(visionImages, sellerId);
        if (request.getImages().size() > 3) {
            productImageService.assertOwned(request.getImages().subList(3, request.getImages().size()), sellerId);
        }
        String notes = StringUtils.hasText(request.getNotes()) ? request.getNotes().trim() : "卖家没有补充说明";
        if (request.getImages().size() > 3) {
            notes = notes + "（另有" + (request.getImages().size() - 3) + "张实拍图未送入模型，请结合备注判断）";
        }
        AiListingAnalysis analysis = parse(
                aiModelClient.completeJson(LISTING_SYSTEM_PROMPT, "卖家补充说明：" + notes, dataUrls),
                AiListingAnalysis.class
        );
        normalizeAnalysis(analysis);

        String comparableKeyword = firstKeyword(analysis);
        PageResult<ProductListVO> comparablePage = productService.search(
                comparableKeyword, analysis.getCategory(), null, null, "price_asc", 1, 20
        );
        List<BigDecimal> prices = comparablePage.getList() == null ? new ArrayList<>() : comparablePage.getList().stream()
                .map(ProductListVO::getPrice)
                .filter(price -> price != null && price.signum() > 0)
                .sorted()
                .toList();

        BigDecimal marketLow = percentile(prices, 0.25);
        BigDecimal marketHigh = percentile(prices, 0.75);
        BigDecimal marketMedian = percentile(prices, 0.50);
        BigDecimal suggestedPrice;
        String pricingBasis;
        if (marketMedian != null) {
            suggestedPrice = marketMedian.multiply(conditionFactor(analysis.getCondition()))
                    .setScale(2, RoundingMode.HALF_UP);
            pricingBasis = "参考站内" + prices.size() + "件同类在售商品，并按“" + analysis.getCondition() + "”成色调整";
        } else if (analysis.getReferencePrice() != null && analysis.getReferencePrice().signum() > 0) {
            suggestedPrice = analysis.getReferencePrice().setScale(2, RoundingMode.HALF_UP);
            marketLow = suggestedPrice;
            marketHigh = suggestedPrice;
            pricingBasis = "站内暂无足够同类商品，暂采用图片识别参考价，请卖家重点确认";
        } else {
            suggestedPrice = null;
            pricingBasis = "没有足够的站内同类价格，AI 未自动填写价格";
        }

        String description = "【成色】" + analysis.getCondition() + "\n" + analysis.getDescription();
        return AiListingDraftResponse.builder()
                .title(analysis.getTitle())
                .description(description)
                .category(analysis.getCategory())
                .condition(analysis.getCondition())
                .suggestedPrice(suggestedPrice)
                .marketPriceLow(marketLow)
                .marketPriceHigh(marketHigh)
                .comparableCount(prices.size())
                .pricingBasis(pricingBasis)
                .confidence(analysis.getConfidence())
                .warnings(analysis.getWarnings())
                .build();
    }

    private void normalizeIntent(AiSearchIntent intent) {
        if (intent == null || !StringUtils.hasText(intent.getKeyword())) {
            throw new BizException(ResultCode.AI_RESPONSE_INVALID);
        }
        intent.setKeyword(intent.getKeyword().trim());
        if (!CATEGORIES.contains(intent.getCategory())) intent.setCategory(null);
        if (!SORTS.contains(intent.getSort())) intent.setSort("time_desc");
        if (intent.getMinPrice() != null && intent.getMinPrice().signum() < 0) intent.setMinPrice(null);
        if (intent.getMaxPrice() != null && intent.getMaxPrice().signum() < 0) intent.setMaxPrice(null);
        if (intent.getMinPrice() != null && intent.getMaxPrice() != null
                && intent.getMinPrice().compareTo(intent.getMaxPrice()) > 0) {
            BigDecimal min = intent.getMaxPrice();
            intent.setMaxPrice(intent.getMinPrice());
            intent.setMinPrice(min);
        }
    }

    private void normalizeAnalysis(AiListingAnalysis analysis) {
        if (analysis == null || !StringUtils.hasText(analysis.getTitle()) || !StringUtils.hasText(analysis.getDescription())) {
            throw new BizException(ResultCode.AI_RESPONSE_INVALID);
        }
        analysis.setTitle(limit(analysis.getTitle().trim(), 50));
        analysis.setDescription(limit(analysis.getDescription().trim(), 500));
        if (!CATEGORIES.contains(analysis.getCategory())) analysis.setCategory("其他");
        if (!Set.of("全新", "几乎全新", "良好", "一般", "较差").contains(analysis.getCondition())) {
            analysis.setCondition("一般");
        }
        if (analysis.getWarnings() == null) analysis.setWarnings(List.of());
        if (analysis.getConfidence() == null) analysis.setConfidence(0.0);
        analysis.setConfidence(Math.max(0.0, Math.min(1.0, analysis.getConfidence())));
    }

    private String firstKeyword(AiListingAnalysis analysis) {
        if (analysis.getKeywords() != null) {
            for (String keyword : analysis.getKeywords()) {
                if (StringUtils.hasText(keyword)) return keyword.trim();
            }
        }
        return analysis.getTitle();
    }

    private BigDecimal percentile(List<BigDecimal> sorted, double percentile) {
        if (sorted.isEmpty()) return null;
        int index = (int) Math.round((sorted.size() - 1) * percentile);
        return sorted.get(index).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal conditionFactor(String condition) {
        return switch (condition) {
            case "全新" -> new BigDecimal("1.05");
            case "几乎全新" -> BigDecimal.ONE;
            case "良好" -> new BigDecimal("0.90");
            case "一般" -> new BigDecimal("0.75");
            case "较差" -> new BigDecimal("0.55");
            default -> new BigDecimal("0.75");
        };
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private <T> T parse(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new BizException(ResultCode.AI_RESPONSE_INVALID);
        }
    }

    private <T> T withAiPermit(Supplier<T> action) {
        if (!AI_FLOW_CONCURRENCY.tryAcquire()) {
            throw new BizException(429, "AI 服务繁忙，请稍后重试");
        }
        try {
            return action.get();
        } finally {
            AI_FLOW_CONCURRENCY.release();
        }
    }
}
