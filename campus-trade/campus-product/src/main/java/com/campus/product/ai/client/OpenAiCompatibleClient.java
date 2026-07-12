package com.campus.product.ai.client;

import com.campus.common.exception.BizException;
import com.campus.common.result.ResultCode;
import com.campus.product.ai.dto.AiRuntimeSettings;
import com.campus.product.ai.service.AiSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleClient implements AiModelClient {

    private static final Semaphore AI_CONCURRENCY = new Semaphore(4, true);

    private final AiSettingsService aiSettingsService;

    @Override
    public String completeJson(String systemPrompt, String userText, List<String> imageDataUrls) {
        if (!AI_CONCURRENCY.tryAcquire()) {
            throw new BizException(429, "AI 服务繁忙，请稍后重试");
        }
        try {
            return doCompleteJson(systemPrompt, userText, imageDataUrls);
        } finally {
            AI_CONCURRENCY.release();
        }
    }

    private String doCompleteJson(String systemPrompt, String userText, List<String> imageDataUrls) {
        AiRuntimeSettings settings = aiSettingsService.resolve();
        if (!StringUtils.hasText(settings.getApiKey())) {
            throw new BizException(ResultCode.AI_NOT_CONFIGURED);
        }
        if (imageDataUrls != null && !imageDataUrls.isEmpty() && !settings.isSupportsVision()) {
            throw new BizException(ResultCode.AI_VISION_NOT_SUPPORTED);
        }

        Object userContent = buildUserContent(userText, imageDataUrls);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", settings.getModel());
        body.put("temperature", 0.2);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userContent)
        ));

        try {
            JsonNode response = buildClient(settings).post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + settings.getApiKey())
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode content = response == null ? null : response.at("/choices/0/message/content");
            if (content == null || !content.isTextual() || content.asText().isBlank()) {
                throw new BizException(ResultCode.AI_RESPONSE_INVALID);
            }
            return stripCodeFence(content.asText());
        } catch (BizException e) {
            throw e;
        } catch (org.springframework.web.client.UnknownContentTypeException e) {
            log.error("调用 AI 模型失败（非 JSON 响应）: model={}, baseUrl={}",
                    settings.getModel(), settings.getBaseUrl(), e);
            throw new BizException(502, "API 返回了非 JSON 响应，请检查 Base URL 是否以 /v1 结尾");
        } catch (Exception e) {
            log.error("调用 AI 模型失败: model={}", settings.getModel(), e);
            throw new BizException(500, "AI 模型调用失败，请稍后重试");
        }
    }

    private RestClient buildClient(AiRuntimeSettings settings) {
        Duration timeout = Duration.ofSeconds(Math.max(1, settings.getTimeoutSeconds()));
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);
        return RestClient.builder()
                .baseUrl(AiEndpointValidator.normalizeOpenAiCompatibleBaseUrl(settings.getBaseUrl()))
                .requestFactory(factory)
                .build();
    }

    private Object buildUserContent(String userText, List<String> imageDataUrls) {
        if (imageDataUrls == null || imageDataUrls.isEmpty()) {
            return userText;
        }
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", userText));
        // 控制视觉请求体积：最多送 3 张图给模型
        int limit = Math.min(3, imageDataUrls.size());
        for (int i = 0; i < limit; i++) {
            content.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", imageDataUrls.get(i), "detail", "low")
            ));
        }
        return content;
    }

    private String stripCodeFence(String content) {
        String value = content.trim();
        if (value.startsWith("```")) {
            int firstLineEnd = value.indexOf('\n');
            int closing = value.lastIndexOf("```");
            if (firstLineEnd >= 0 && closing > firstLineEnd) {
                value = value.substring(firstLineEnd + 1, closing).trim();
            }
        }
        return value;
    }

}
