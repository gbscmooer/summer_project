package com.campus.gateway.filter;

import com.campus.common.result.Result;
import com.campus.common.util.JwtUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 网关全局鉴权过滤器（Spring WebFlux 响应式，禁止使用 Servlet API）。
 *
 * <p>职责：
 * <ol>
 *   <li>白名单路径放行（注册/登录/商品列表/搜索/商品详情 GET）。</li>
 *   <li>无论是否白名单，先剥离客户端伪造的 {@code X-User-Id} 头，防止越权。</li>
 *   <li>非白名单请求校验 {@code Authorization: Bearer <token>}，无效则返回 401 JSON。</li>
 *   <li>有效 token 解析出 userId，通过 {@code X-User-Id} 透传给下游服务。</li>
 * </ol>
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    /** 下游服务约定的当前用户标识请求头。 */
    private static final String HEADER_USER_ID = "X-User-Id";

    /** Bearer 前缀。 */
    private static final String BEARER_PREFIX = "Bearer ";

    /** 商品详情白名单：GET /api/product/{纯数字id}。 */
    private static final Pattern PRODUCT_DETAIL_PATTERN = Pattern.compile("^/api/product/\\d+$");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        // 1. 先剥离客户端伪造的 X-User-Id，防止越权（无论白名单与否都要剥离）。
        ServerHttpRequest sanitizedRequest = request.mutate()
                .headers(headers -> headers.remove(HEADER_USER_ID))
                .build();
        ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitizedRequest).build();

        // 2. 白名单放行（已剥离 X-User-Id，即便携带 token 也无需注入下游）。
        if (isWhiteList(path, method)) {
            return chain.filter(sanitizedExchange);
        }

        // 3. 非白名单：校验 Authorization 头中的 Bearer token。
        String authorization = sanitizedRequest.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String token = extractToken(authorization);
        if (token == null || !JwtUtil.isValid(token)) {
            return unauthorized(sanitizedExchange);
        }

        // 4. token 有效：解析 userId 并通过 X-User-Id 透传给下游。
        Long userId = JwtUtil.parseUserId(token);
        ServerHttpRequest authedRequest = sanitizedRequest.mutate()
                .header(HEADER_USER_ID, String.valueOf(userId))
                .build();
        return chain.filter(sanitizedExchange.mutate().request(authedRequest).build());
    }

    /**
     * 白名单判定：满足任一即跳过鉴权。
     */
    private boolean isWhiteList(String path, HttpMethod method) {
        if ("/api/user/register".equals(path)
                || "/api/user/login".equals(path)
                || "/api/product/list".equals(path)
                || "/api/product/search".equals(path)) {
            return true;
        }
        // 商品详情：GET /api/product/{纯数字id}
        return HttpMethod.GET.equals(method) && PRODUCT_DETAIL_PATTERN.matcher(path).matches();
    }

    /**
     * 从 Authorization 头提取 Bearer token，缺失或格式不符返回 null。
     */
    private String extractToken(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return StringUtils.hasText(token) ? token : null;
    }

    /**
     * 直接写出 HTTP 401 + JSON 响应体 {"code":401,"message":"未登录或Token已失效","data":null}。
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.parseMediaType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"));

        Result<Void> body = Result.error(401, "未登录或Token已失效");
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            // 兜底：序列化失败时手写 JSON，保证响应仍是合法 JSON。
            bytes = "{\"code\":401,\"message\":\"未登录或Token已失效\",\"data\":null}"
                    .getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // 高优先级，先于路由转发执行。
        return -100;
    }
}
