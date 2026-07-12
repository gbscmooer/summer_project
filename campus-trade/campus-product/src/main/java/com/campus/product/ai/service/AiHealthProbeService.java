package com.campus.product.ai.service;

import com.campus.product.ai.client.AiEndpointValidator;
import com.campus.product.ai.dto.AiHealthStatusView;
import com.campus.product.ai.dto.AiRuntimeSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Slf4j
@Service
public class AiHealthProbeService {

    private static final int PROBE_TIMEOUT_SECONDS = 8;

    public AiHealthStatusView probe(AiRuntimeSettings settings) {
        if (settings == null || !StringUtils.hasText(settings.getApiKey())) {
            return AiHealthStatusView.builder()
                    .status("UNCONFIGURED")
                    .message("未配置 API Key，AI 功能不可用")
                    .build();
        }
        String baseUrl = AiEndpointValidator.normalizeOpenAiCompatibleBaseUrl(settings.getBaseUrl());
        Duration timeout = Duration.ofSeconds(PROBE_TIMEOUT_SECONDS);
        long started = System.currentTimeMillis();
        try {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(timeout)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
            factory.setReadTimeout(timeout);
            RestClient client = RestClient.builder()
                    .baseUrl(baseUrl)
                    .requestFactory(factory)
                    .build();
            ResponseEntity<String> response = client.get()
                    .uri("/models")
                    .header("Authorization", "Bearer " + settings.getApiKey())
                    .retrieve()
                    .toEntity(String.class);
            MediaType contentType = response.getHeaders().getContentType();
            if (contentType != null && MediaType.TEXT_HTML.isCompatibleWith(contentType)) {
                return AiHealthStatusView.builder()
                        .status("MISCONFIGURED")
                        .message("API 返回了网页而非 JSON，请确认 Base URL 以 /v1 结尾（如 https://host/v1）")
                        .probeLatencyMs(System.currentTimeMillis() - started)
                        .build();
            }
            if (!response.getStatusCode().is2xxSuccessful()) {
                return AiHealthStatusView.builder()
                        .status("UNREACHABLE")
                        .message("模型列表请求失败，HTTP " + response.getStatusCode().value())
                        .probeLatencyMs(System.currentTimeMillis() - started)
                        .build();
            }
            long latency = System.currentTimeMillis() - started;
            return AiHealthStatusView.builder()
                    .status("OK")
                    .message("API 可达，模型端点响应正常")
                    .probeLatencyMs(latency)
                    .build();
        } catch (Exception e) {
            log.warn("AI health probe failed: baseUrl={}, model={}", settings.getBaseUrl(), settings.getModel(), e);
            String message = e.getMessage() == null ? "无法连接 AI 服务" : e.getMessage();
            if (message.length() > 200) {
                message = message.substring(0, 200);
            }
            return AiHealthStatusView.builder()
                    .status("UNREACHABLE")
                    .message(message)
                    .probeLatencyMs(System.currentTimeMillis() - started)
                    .build();
        }
    }
}
