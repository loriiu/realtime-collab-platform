package com.collab.platform.message.service;

import com.collab.platform.message.redis.RedisKeyManager;
import com.collab.platform.message.ws.WsSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Online status management — tracks which server instance a user is connected to.
 *
 * <p>Uses Redis as primary store with local WsSessionManager as fallback
 * when Redis is unreachable.</p>
 */
@Service
public class OnlineService {

    private static final Logger log = LoggerFactory.getLogger(OnlineService.class);

    private static final long HEARTBEAT_TTL_SECONDS = 120;

    private final StringRedisTemplate stringRedisTemplate;
    private final WsSessionManager wsSessionManager;

    @Value("${server-id:msg-1}")
    private String serverId;

    public OnlineService(StringRedisTemplate stringRedisTemplate,
                         WsSessionManager wsSessionManager) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.wsSessionManager = wsSessionManager;
    }

    /**
     * Mark a user as online on this server instance.
     *
     * @param userId the user ID
     */
    public void markOnline(Long userId) {
        String key = RedisKeyManager.ONLINE_PREFIX + userId;
        stringRedisTemplate.opsForValue().set(key, serverId, HEARTBEAT_TTL_SECONDS, TimeUnit.SECONDS);
        log.debug("User online: userId={}, serverId={}", userId, serverId);
    }

    /**
     * Mark a user as offline (remove online status).
     *
     * @param userId the user ID
     */
    public void markOffline(Long userId) {
        String key = RedisKeyManager.ONLINE_PREFIX + userId;
        stringRedisTemplate.delete(key);
        log.debug("User offline: userId={}", userId);
    }

    /**
     * Check if a user is online. Falls back to local session manager if Redis is unavailable.
     *
     * @param userId the user ID
     * @return true if the user is online
     */
    public boolean isOnline(Long userId) {
        try {
            String key = RedisKeyManager.ONLINE_PREFIX + userId;
            Boolean exists = stringRedisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable, falling back to local session check for userId={}", userId);
            return wsSessionManager.hasSession(userId);
        }
    }

    /**
     * Refresh the heartbeat TTL for a connected user.
     *
     * @param userId the user ID
     */
    public void refreshHeartbeat(Long userId) {
        String key = RedisKeyManager.ONLINE_PREFIX + userId;
        stringRedisTemplate.expire(key, HEARTBEAT_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Batch check online status for a list of user IDs.
     *
     * @param userIds list of user IDs
     * @return map of userId → online status
     */
    public Map<Long, Boolean> batchCheckOnline(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Boolean> result = new HashMap<>();
        for (Long userId : userIds) {
            result.put(userId, isOnline(userId));
        }
        return result;
    }
}
