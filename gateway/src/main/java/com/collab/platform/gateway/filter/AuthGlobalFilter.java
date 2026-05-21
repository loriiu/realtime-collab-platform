package com.collab.platform.gateway.filter;

import com.collab.platform.common.core.result.ResultCode;
import com.collab.platform.common.security.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Global authentication filter for the API Gateway.
 *
 * <p>Whitelisted paths bypass authentication; all others must carry a valid
 * Bearer JWT. On success the resolved user ID is forwarded as {@code X-User-Id}.</p>
 *
 * <p>This filter is stateless — it never calls Redis.</p>
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthGlobalFilter.class);

    /** Paths that do not require authentication. */
    private static final Set<String> WHITELIST = Set.of(
            "/user/login",
            "/user/register"
    );

    /** Prefixes that bypass authentication (e.g. actuator endpoints). */
    private static final Set<String> WHITELIST_PREFIXES = Set.of(
            "/actuator"
    );

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int BEARER_PREFIX_LENGTH = BEARER_PREFIX.length();
    private static final String X_USER_ID_HEADER = "X-User-Id";

    private final JwtUtil jwtUtil;

    public AuthGlobalFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // --- whitelist check ---
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        // --- extract Bearer token ---
        String authHeader = exchange.getRequest().getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return writeUnauthorized(exchange, "Missing or malformed Authorization header");
        }

        String token = authHeader.substring(BEARER_PREFIX_LENGTH);
        if (token.isBlank()) {
            return writeUnauthorized(exchange, "Token is blank");
        }

        // --- validate JWT ---
        Long userId;
        try {
            userId = jwtUtil.parseToken(token);
        } catch (Exception e) {
            return writeUnauthorized(exchange, "Invalid or expired token");
        }

        // --- inject X-User-Id and forward ---
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(X_USER_ID_HEADER, String.valueOf(userId))
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    // ----------------------------------------------------------------- helpers

    private boolean isWhitelisted(String path) {
        if (WHITELIST.contains(path)) {
            return true;
        }
        for (String prefix : WHITELIST_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> writeUnauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"code\":%d,\"message\":\"%s\",\"data\":null}",
                ResultCode.UNAUTHORIZED.getCode(), message);
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
