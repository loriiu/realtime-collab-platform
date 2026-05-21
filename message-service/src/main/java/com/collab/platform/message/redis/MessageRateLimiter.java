package com.collab.platform.message.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Rate limiter for WebSocket messages — 20 messages per second per user.
 */
@Component
public class MessageRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(MessageRateLimiter.class);

    private static final int MAX_MESSAGES_PER_SECOND = 20;
    private static final long WINDOW_SECONDS = 1;

    private final StringRedisTemplate stringRedisTemplate;

    public MessageRateLimiter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * Try to acquire a message-send permit for the given user.
     *
     * @param userId the user ID
     * @return true if the message is allowed, false if rate-limited
     */
    public boolean tryAcquire(Long userId) {
        String key = RedisKeyManager.RATELIMIT_PREFIX + userId;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count == null) {
            return true;
        }
        // Set expiry on first increment
        if (count == 1) {
            stringRedisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
        }
        if (count > MAX_MESSAGES_PER_SECOND) {
            log.warn("Rate limit exceeded: userId={}, count={}", userId, count);
            return false;
        }
        return true;
    }
}
