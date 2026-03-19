package com.wwt.pixel.gateway.filter;

import com.wwt.pixel.common.constant.CommonConstant;
import com.wwt.pixel.common.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * JWT认证过滤器 - 在Gateway层统一验证Token
 */
@Slf4j
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final String ADMIN_TOKEN_KEY_PREFIX = "admin:token:";
    private static final String USER_TOKEN_KEY_PREFIX = "user:token:";

    @Value("${pixel.jwt.secret}")
    private String jwtSecret;

    @Value("${pixel.admin.jwt.secret}")
    private String adminJwtSecret;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ReactiveStringRedisTemplate redisTemplate;

    public JwtAuthFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 白名单路径 - 无需登录即可访问
     */
    private static final List<String> WHITE_LIST = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/username/check",
            "/api/auth/email/check",
            "/api/auth/email/send-code",
            "/api/auth/phone/send-code",
            "/api/auth/send-code",
            "/api/auth/login-phone",
            "/api/admin/auth/login",
            "/api/style/**",
            "/api/image/hot/list",
            "/api/health",
            "/actuator/**"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 白名单直接放行
        if (isWhiteListed(path)) {
            return chain.filter(exchange);
        }

        // 获取Token
        String authHeader = request.getHeaders().getFirst(CommonConstant.HEADER_TOKEN);
        String token = JwtUtil.extractToken(authHeader);

        // 判断是否管理后台请求
        boolean isAdminPath = path.startsWith("/api/admin/");
        String secret = isAdminPath ? adminJwtSecret : jwtSecret;

        // 验证Token
        if (token == null || !JwtUtil.validateToken(token, secret)) {
            log.warn("请求被拒绝, path={}, 原因: Token无效", path);
            return unauthorized(exchange);
        }

        // 解析用户信息，添加到Header传递给下游服务
        Long userId = JwtUtil.getUserIdFromToken(token, secret);
        String username = JwtUtil.getUsernameFromToken(token, secret);

        if (userId == null) {
            return unauthorized(exchange);
        }

        String tokenKey = buildTokenKey(isAdminPath, userId);
        return redisTemplate.opsForValue().get(tokenKey)
                .defaultIfEmpty("")
                .flatMap(cachedToken -> {
                    if (!token.equals(cachedToken)) {
                        log.warn("请求被拒绝, path={}, userId={}, 原因: 会话已失效", path, userId);
                        return unauthorized(exchange);
                    }

                    // 将用户信息添加到请求Header (管理员和普通用户使用不同Header)
                    ServerHttpRequest newRequest;
                    if (isAdminPath) {
                        newRequest = request.mutate()
                                .header(CommonConstant.HEADER_ADMIN_ID, String.valueOf(userId))
                                .header(CommonConstant.HEADER_ADMIN_NAME, username)
                                .build();
                    } else {
                        newRequest = request.mutate()
                                .header(CommonConstant.HEADER_USER_ID, String.valueOf(userId))
                                .header(CommonConstant.HEADER_USERNAME, username)
                                .build();
                    }

                    log.debug("请求通过认证, path={}, userId={}, isAdmin={}", path, userId, isAdminPath);
                    return chain.filter(exchange.mutate().request(newRequest).build());
                })
                .onErrorResume(error -> {
                    log.error("Redis会话校验失败, path={}, userId={}", path, userId, error);
                    return unauthorized(exchange);
                });
    }

    private boolean isWhiteListed(String path) {
        return WHITE_LIST.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private String buildTokenKey(boolean isAdminPath, Long userId) {
        return (isAdminPath ? ADMIN_TOKEN_KEY_PREFIX : USER_TOKEN_KEY_PREFIX) + userId;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        return -100; // 优先级最高
    }
}
