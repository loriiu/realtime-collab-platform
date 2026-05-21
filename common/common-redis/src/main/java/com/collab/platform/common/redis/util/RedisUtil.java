package com.collab.platform.common.redis.util;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Lightweight Redis helper wrapping {@link StringRedisTemplate}.
 */
@Component
public class RedisUtil {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisUtil(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * Store a value with an expiration timeout.
     *
     * @param key     Redis key
     * @param value   value to store (serialized via toString)
     * @param timeout duration
     * @param unit    time unit of the timeout
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, String.valueOf(value), timeout, unit);
    }

    /**
     * Retrieve the value stored at {@code key}.
     *
     * @param key Redis key
     * @return the stored value, or {@code null} if the key is absent
     */
    public Object get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /**
     * Delete a key.
     *
     * @param key Redis key
     * @return {@code true} if the key was deleted
     */
    public Boolean delete(String key) {
        return stringRedisTemplate.delete(key);
    }

    /**
     * Check whether a key exists.
     *
     * @param key Redis key
     * @return {@code true} if the key exists
     */
    public Boolean hasKey(String key) {
        return stringRedisTemplate.hasKey(key);
    }
}
