package com.campus.user.mail;

import com.campus.common.exception.BizException;
import com.campus.common.result.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resend HTTP API 发信（不依赖官方 SDK，便于容器内使用）。
 */
@Slf4j
@Service
public class ResendMailService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${campus.mail.resend-api-key:}")
    private String apiKey;

    @Value("${campus.mail.from:Campus Market <onboarding@resend.dev>}")
    private String from;

    public void sendHtml(String toEmail, String subject, String html) {
        if (!StringUtils.hasText(apiKey)) {
            log.error("RESEND_API_KEY 未配置，无法发信");
            throw new BizException(ResultCode.MAIL_SEND_FAILED);
        }
        if (!StringUtils.hasText(toEmail)) {
            throw new BizException(ResultCode.MAIL_SEND_FAILED);
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("from", from);
            body.put("to", List.of(toEmail));
            body.put("subject", subject);
            body.put("html", html);
            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Resend 发信失败 status={} body={}", response.statusCode(), response.body());
                throw new BizException(ResultCode.MAIL_SEND_FAILED);
            }
            log.info("Resend 发信成功 to={}", mask(toEmail));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Resend 发信异常 to={}", mask(toEmail), e);
            throw new BizException(ResultCode.MAIL_SEND_FAILED);
        }
    }

    private static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
