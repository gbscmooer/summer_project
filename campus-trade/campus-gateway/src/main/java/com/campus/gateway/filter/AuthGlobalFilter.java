package com.campus.gateway.filter;

import com.campus.common.result.Result;
import com.campus.common.util.JwtUtil;
import com.campus.common.security.InternalApiTokenValidator;
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
import reactor.core.scheduler.Schedulers;

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

    /** 商品留言列表：GET /api/product/{纯数字id}/comments */
    private static final Pattern PRODUCT_COMMENT_PATTERN = Pattern.compile("^/api/product/\\d+/comments$");

    /** 话题帖子列表/详情/按用户：GET /api/topic/posts/list、/api/topic/posts/{id}、/api/topic/posts/by-user/{id} */
    private static final Pattern TOPIC_POST_LIST_PATTERN = Pattern.compile("^/api/topic/posts/list$");
    private static final Pattern TOPIC_POST_BY_USER_PATTERN = Pattern.compile("^/api/topic/posts/by-user/\\d+$");
    private static final Pattern TOPIC_POST_DETAIL_PATTERN = Pattern.compile("^/api/topic/posts/\\d+$");

    /** 公开用户主页：GET /api/user/profile/{纯数字id} */
    private static final Pattern USER_PROFILE_PATTERN = Pattern.compile("^/api/user/profile/\\d+$");

    /** 公开关注统计：GET /api/user/follow/followers|following */
    private static final Pattern USER_FOLLOW_LIST_PATTERN =
            Pattern.compile("^/api/user/follow/(followers|following)$");

    /** 商品图片公开读取：GET /api/product/image/{安全文件名}。 */
    private static final Pattern PRODUCT_IMAGE_PATTERN = Pattern.compile("^/api/product/image/[a-zA-Z0-9._-]+$");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        // 1. 先剥离客户端伪造的 X-User-Id，防止越权（无论白名单与否都要剥离）。
        ServerHttpRequest sanitizedRequest = request.mutate()
                .headers(headers -> {
                    headers.remove(HEADER_USER_ID);
                    headers.remove(InternalApiTokenValidator.HEADER_NAME);
                })
                .build();
        ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitizedRequest).build();

        // 2. Internal endpoints are never reachable through the public gateway.
        if (isInternalPath(path)) {
            return notFound(sanitizedExchange);
        }

        // 3. CORS 预检请求直接放行。
        if (HttpMethod.OPTIONS.equals(method)) {
            return chain.filter(sanitizedExchange);
        }

        // 4. 白名单放行；若携带有效 token 则可选注入 X-User-Id（用于关注态等）。
        if (isWhiteList(path, method)) {
            return injectOptionalUser(sanitizedExchange, sanitizedRequest, chain);
        }

        // 5. 非白名单：校验 Authorization 头中的 Bearer token（JWT 解析放到 boundedElastic，避免阻塞 Netty event loop）。
        String authorization = sanitizedRequest.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String token = extractToken(authorization);
        if (token == null) {
            return unauthorized(sanitizedExchange);
        }

        return Mono.fromCallable(() -> JwtUtil.parseUserId(token))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(userId -> {
                    ServerHttpRequest authedRequest = sanitizedRequest.mutate()
                            .header(HEADER_USER_ID, String.valueOf(userId))
                            .build();
                    return chain.filter(sanitizedExchange.mutate().request(authedRequest).build());
                })
                .onErrorResume(e -> unauthorized(sanitizedExchange));
    }

    /**
     * 白名单判定：满足任一即跳过鉴权。
     */
    boolean isWhiteList(String path, HttpMethod method) {
        if (HttpMethod.POST.equals(method) && ("/api/user/register".equals(path)
                || "/api/user/login".equals(path))) {
            return true;
        }
        if (HttpMethod.GET.equals(method) && ("/api/product/list".equals(path)
                || "/api/product/search".equals(path)
                || "/api/product/tutorial".equals(path))) {
            return true;
        }
        if (HttpMethod.GET.equals(method) && PRODUCT_IMAGE_PATTERN.matcher(path).matches()) {
            return true;
        }
        // 商品详情：GET /api/product/{纯数字id}
        if (HttpMethod.GET.equals(method) && PRODUCT_DETAIL_PATTERN.matcher(path).matches()) {
            return true;
        }
        // 商品留言列表：GET /api/product/{纯数字id}/comments
        if (HttpMethod.GET.equals(method) && PRODUCT_COMMENT_PATTERN.matcher(path).matches()) {
            return true;
        }
        // 公开用户主页（可选注入 X-User-Id 以返回 following）
        if (HttpMethod.GET.equals(method) && USER_PROFILE_PATTERN.matcher(path).matches()) {
            return true;
        }
        // 公开关注统计
        if (HttpMethod.GET.equals(method) && USER_FOLLOW_LIST_PATTERN.matcher(path).matches()) {
            return true;
        }
        // 话题帖子：列表、按用户、详情
        if (HttpMethod.GET.equals(method) && TOPIC_POST_LIST_PATTERN.matcher(path).matches()) {
            return true;
        }
        if (HttpMethod.GET.equals(method) && TOPIC_POST_BY_USER_PATTERN.matcher(path).matches()) {
            return true;
        }
        return HttpMethod.GET.equals(method) && TOPIC_POST_DETAIL_PATTERN.matcher(path).matches();
    }

    /**
     * 白名单请求：无 token 直接放行；有 token 则尽力解析并注入 X-User-Id，解析失败仍放行。
     */
    private Mono<Void> injectOptionalUser(
            ServerWebExchange exchange,
            ServerHttpRequest sanitizedRequest,
            GatewayFilterChain chain) {
        String authorization = sanitizedRequest.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String token = extractToken(authorization);
        if (token == null) {
            return chain.filter(exchange);
        }
        return Mono.fromCallable(() -> JwtUtil.parseUserId(token))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(userId -> {
                    ServerHttpRequest authedRequest = sanitizedRequest.mutate()
                            .header(HEADER_USER_ID, String.valueOf(userId))
                            .build();
                    return chain.filter(exchange.mutate().request(authedRequest).build());
                })
                .onErrorResume(e -> chain.filter(exchange));
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

    boolean isInternalPath(String path) {
        return "/api/user/batch".equals(path)
                || path.startsWith("/api/user/internal/")
                || path.startsWith("/api/product/inner/");
    }

    private Mono<Void> notFound(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.NOT_FOUND);
        response.getHeaders().setContentType(MediaType.parseMediaType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"));
        byte[] bytes = "{\"code\":404,\"message\":\"资源不存在\",\"data\":null}"
                .getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
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
