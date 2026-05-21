package com.collab.platform.message.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis message publisher — sends messages to a Redis channel for cross-instance delivery.
 */
@Component
public class RedisMessagePublisher {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisMessagePublisher(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * Publish a message to a Redis channel.
     *
     * @param channel the Redis channel name
     * @param message the serialized message payload
     */
    public void publish(String channel, String message) {
        stringRedisTemplate.convertAndSend(channel, message);
    }
}
