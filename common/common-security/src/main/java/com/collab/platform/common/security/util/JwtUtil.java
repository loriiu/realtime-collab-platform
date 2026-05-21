package com.collab.platform.common.security.util;

import com.collab.platform.common.core.exception.BizException;
import com.collab.platform.common.core.result.ResultCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT utility for token creation and parsing.
 * Reads {@code jwt.secret} and {@code jwt.ttl} from application configuration.
 *
 * <p>The filter layer strips "Bearer " before calling these methods;
 * they operate on the raw token string.</p>
 */
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtUtil {

    /** HMAC secret key string, injected from config (min 32 characters). */
    private String secret;
    /** Token time-to-live in seconds, injected from config. */
    private long ttl;

    private volatile SecretKey secretKey;

    public void setSecret(String secret) {
        this.secret = secret;
        this.secretKey = null; // force re-derive
    }

    public void setTtl(long ttl) {
        this.ttl = ttl;
    }

    /**
     * Lazily derive the {@link SecretKey} from the configured secret string.
     */
    private SecretKey getSecretKey() {
        if (secretKey == null) {
            synchronized (this) {
                if (secretKey == null) {
                    if (secret == null || secret.length() < 32) {
                        throw new IllegalStateException("jwt.secret must be at least 32 characters");
                    }
                    secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        return secretKey;
    }

    /**
     * Create a signed JWT for the given user ID.
     *
     * @param userId the authenticated user's ID
     * @return compact JWT string (no "Bearer " prefix)
     */
    public String createToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttl)))
                .signWith(getSecretKey())
                .compact();
    }

    /**
     * Parse a raw token and extract the user ID.
     *
     * @param token raw JWT string (without "Bearer " prefix)
     * @return the user ID stored in the token
     * @throws BizException with code 401 if the token is invalid or expired
     */
    public Long parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "Invalid or expired token");
        }
    }
}
