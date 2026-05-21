package com.collab.platform.message.redis;

/**
 * Redis key constants for the message service.
 */
public final class RedisKeyManager {

    private RedisKeyManager() {
        // utility class
    }

    /** Online status key prefix: collab:msg:online:{userId} → serverId */
    public static final String ONLINE_PREFIX = "collab:msg:online:";

    /** Message dedup key prefix: collab:msg:dedup:{messageId} → "1" */
    public static final String DEDUP_PREFIX = "collab:msg:dedup:";

    /** Rate limit key prefix: collab:msg:ratelimit:{userId} → count */
    public static final String RATELIMIT_PREFIX = "collab:msg:ratelimit:";

    /** User info cache key prefix: collab:user:cache:{userId} → JSON */
    public static final String USER_CACHE_PREFIX = "collab:user:cache:";

    /** Redis Pub/Sub channel for cross-instance message broadcast */
    public static final String CHANNEL = "collab:msg:chat";
}
