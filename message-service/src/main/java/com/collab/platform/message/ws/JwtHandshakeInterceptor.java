package com.collab.platform.message.ws;

import com.collab.platform.common.security.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

/**
 * JWT authentication interceptor for WebSocket handshake.
 * Extracts token from URI query parameter and validates it.
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);

    private final JwtUtil jwtUtil;

    public JwtHandshakeInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        URI uri = request.getURI();
        String query = uri.getQuery();
        if (query == null || !query.contains("token=")) {
            log.warn("WebSocket handshake rejected: missing token parameter");
            return false;
        }

        String token = extractToken(query);
        if (token == null || token.isBlank()) {
            log.warn("WebSocket handshake rejected: empty token");
            return false;
        }

        try {
            Long userId = jwtUtil.parseToken(token);
            attributes.put("userId", userId);
            log.info("WebSocket handshake accepted: userId={}", userId);
            return true;
        } catch (Exception e) {
            log.warn("WebSocket handshake rejected: invalid token — {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    /**
     * Extract the token value from query string like "token=xxx" or "foo=bar&token=xxx".
     */
    private String extractToken(String query) {
        String[] params = query.split("&");
        for (String param : params) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "token".equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }
}
