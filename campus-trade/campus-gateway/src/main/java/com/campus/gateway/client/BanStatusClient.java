package com.campus.gateway.client;

import com.campus.common.security.InternalApiTokenValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 向用户服务查询账号是否处于有效封禁，供网关在 JWT 校验后二次拦截。
 */
@Component
public class BanStatusClient {

    public enum AccessDecision {
        ALLOW,
        BANNED,
        UNAVAILABLE
    }

    private static final Logger log = LoggerFactory.getLogger(BanStatusClient.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMillis(800);
    private static final long ACTIVE_CACHE_MS = 3_000L;
    private static final long BANNED_CACHE_MS = 3_000L;

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    public BanStatusClient(
            ReactorLoadBalancerExchangeFilterFunction lbFunction,
            @Value("${campus.internal-api-token:}") String internalApiToken) {
        this.webClient = WebClient.builder()
                .filter(lbFunction)
                .defaultHeader(InternalApiTokenValidator.HEADER_NAME, internalApiToken)
                .build();
    }

    public Mono<AccessDecision> check(Long userId) {
        if (userId == null) {
            return Mono.just(AccessDecision.ALLOW);
        }
        CacheEntry cached = cache.get(userId);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expireAtMs > now) {
            return Mono.just(cached.decision);
        }

        return webClient.get()
                .uri("http://campus-user/user/internal/access-status?userId={id}", userId)
                .retrieve()
                .onStatus(HttpStatusCode::isError,
                        resp -> Mono.error(new IllegalStateException("ban-check-http-" + resp.statusCode().value())))
                .bodyToMono(String.class)
                .timeout(REQUEST_TIMEOUT)
                .map(body -> parse(body, userId))
                .doOnNext(decision -> {
                    if (decision == AccessDecision.ALLOW || decision == AccessDecision.BANNED) {
                        long ttl = decision == AccessDecision.BANNED ? BANNED_CACHE_MS : ACTIVE_CACHE_MS;
                        cache.put(userId, new CacheEntry(decision, now + ttl));
                    }
                })
                .onErrorResume(ex -> {
                    log.warn("封禁状态查询失败，拒绝放行. userId={}", userId, ex);
                    return Mono.just(AccessDecision.UNAVAILABLE);
                });
    }

    private AccessDecision parse(String body, Long userId) {
        try {
            JsonNode root = objectMapper.readTree(body);
            int code = root.path("code").asInt(-1);
            if (code != 200) {
                log.warn("封禁状态业务码异常. userId={}, code={}", userId, code);
                return AccessDecision.UNAVAILABLE;
            }
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                return AccessDecision.UNAVAILABLE;
            }
            return data.path("banned").asBoolean(false) ? AccessDecision.BANNED : AccessDecision.ALLOW;
        } catch (Exception e) {
            log.warn("解析封禁状态失败. userId={}", userId, e);
            return AccessDecision.UNAVAILABLE;
        }
    }

    private record CacheEntry(AccessDecision decision, long expireAtMs) {
    }
}
