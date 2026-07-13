package com.campus.gateway.filter;

import com.campus.common.result.Result;
import com.campus.common.util.JwtUtil;
import com.campus.common.security.InternalApiTokenValidator;
import com.campus.gateway.client.BanStatusClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
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
 *   <li>有效 token 解析出 userId，查询用户封禁状态，再通过 {@code X-User-Id} 透传给下游服务。</li>
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

    /** 话题帖子公开读：列表/Feed/热榜/详情/按用户/评论列表 */
    private static final Pattern TOPIC_POST_LIST_PATTERN = Pattern.compile("^/api/topic/posts/list$");
    private static final Pattern TOPIC_POST_FEED_PATTERN = Pattern.compile("^/api/topic/posts/feed$");
    private static final Pattern TOPIC_POST_TRENDING_PATTERN = Pattern.compile("^/api/topic/posts/trending$");
    private static final Pattern TOPIC_POST_BY_USER_PATTERN = Pattern.compile("^/api/topic/posts/by-user/\\d+$");
    private static final Pattern TOPIC_POST_DETAIL_PATTERN = Pattern.compile("^/api/topic/posts/\\d+$");
    private static final Pattern TOPIC_POST_COMMENTS_PATTERN = Pattern.compile("^/api/topic/posts/\\d+/comments$");

    /** 公开用户主页：GET /api/user/profile/{纯数字id} */
    private static final Pattern USER_PROFILE_PATTERN = Pattern.compile("^/api/user/profile/\\d+$");

    /** 卖家评价列表：GET /api/order/reviews/seller/{纯数字id} */
    private static final Pattern ORDER_SELLER_REVIEWS_PATTERN =
            Pattern.compile("^/api/order/reviews/seller/\\d+$");

    /** 公开关注统计：GET /api/user/follow/followers|following */
    private static final Pattern USER_FOLLOW_LIST_PATTERN =
            Pattern.compile("^/api/user/follow/(followers|following)$");

    /** 商品图片公开读取：GET /api/product/image/{安全文件名}。 */
    private static final Pattern PRODUCT_IMAGE_PATTERN = Pattern.compile("^/api/product/image/[a-zA-Z0-9._-]+$");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BanStatusClient banStatusClient;

    /** 单测用无参构造：跳过封禁二次校验。 */
    public AuthGlobalFilter() {
        this.banStatusClient = null;
    }

    @Autowired
    public AuthGlobalFilter(BanStatusClient banStatusClient) {
        this.banStatusClient = banStatusClient;
    }

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
                .flatMap(userId -> enforceAccess(userId)
                        .flatMap(decision -> {
                            if (decision == BanStatusClient.AccessDecision.BANNED) {
                                return forbidden(sanitizedExchange, 1007, "账号已被封禁，请联系管理员");
                            }
                            if (decision == BanStatusClient.AccessDecision.UNAVAILABLE) {
                                return serviceUnavailable(sanitizedExchange);
                            }
                            ServerHttpRequest authedRequest = sanitizedRequest.mutate()
                                    .header(HEADER_USER_ID, String.valueOf(userId))
                                    .build();
                            return chain.filter(sanitizedExchange.mutate().request(authedRequest).build());
                        }))
                .onErrorResume(e -> unauthorized(sanitizedExchange));
    }

    private Mono<BanStatusClient.AccessDecision> enforceAccess(Long userId) {
        if (banStatusClient == null) {
            return Mono.just(BanStatusClient.AccessDecision.ALLOW);
        }
        return banStatusClient.check(userId);
    }

    /**
     * 白名单判定：满足任一即跳过鉴权。
     */
    boolean isWhiteList(String path, HttpMethod method) {
        if (HttpMethod.POST.equals(method) && ("/api/user/register".equals(path)
                || "/api/user/login".equals(path)
                || "/api/user/forgot-password".equals(path)
                || "/api/user/reset-password".equals(path))) {
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
        // 卖家评价列表公开读
        if (HttpMethod.GET.equals(method) && ORDER_SELLER_REVIEWS_PATTERN.matcher(path).matches()) {
            return true;
        }
        // 公开关注统计
        if (HttpMethod.GET.equals(method) && USER_FOLLOW_LIST_PATTERN.matcher(path).matches()) {
            return true;
        }
        // 话题帖子：列表、Feed、热榜、按用户、详情、评论列表
        if (HttpMethod.GET.equals(method) && (TOPIC_POST_LIST_PATTERN.matcher(path).matches()
                || TOPIC_POST_FEED_PATTERN.matcher(path).matches()
                || TOPIC_POST_TRENDING_PATTERN.matcher(path).matches()
                || TOPIC_POST_BY_USER_PATTERN.matcher(path).matches()
                || TOPIC_POST_DETAIL_PATTERN.matcher(path).matches()
                || TOPIC_POST_COMMENTS_PATTERN.matcher(path).matches())) {
            return true;
        }
        return false;
    }

    /**
     * 白名单请求：无 token 直接放行；有 token 则尽力解析并注入 X-User-Id，解析失败仍放行。
     * 已封禁用户即使访问公开读接口也不注入身份，避免个性化写侧效应。
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
                .flatMap(userId -> enforceAccess(userId)
                        .flatMap(decision -> {
                            if (decision != BanStatusClient.AccessDecision.ALLOW) {
                                return chain.filter(exchange);
                            }
                            ServerHttpRequest authedRequest = sanitizedRequest.mutate()
                                    .header(HEADER_USER_ID, String.valueOf(userId))
                                    .build();
                            return chain.filter(exchange.mutate().request(authedRequest).build());
                        }))
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
        return writeJson(exchange, HttpStatus.UNAUTHORIZED, Result.error(401, "未登录或Token已失效"));
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, int code, String message) {
        return writeJson(exchange, HttpStatus.FORBIDDEN, Result.error(code, message));
    }

    private Mono<Void> serviceUnavailable(ServerWebExchange exchange) {
        return writeJson(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                Result.error(503, "账号状态校验暂时不可用，请稍后重试"));
    }

    private Mono<Void> writeJson(ServerWebExchange exchange, HttpStatus status, Result<?> body) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.parseMediaType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"));
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = ("{\"code\":" + status.value() + ",\"message\":\"" + status.getReasonPhrase()
                    + "\",\"data\":null}").getBytes(StandardCharsets.UTF_8);
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
