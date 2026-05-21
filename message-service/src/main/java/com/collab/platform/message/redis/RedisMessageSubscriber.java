package com.collab.platform.message.redis;

import com.collab.platform.message.dto.WsMessageDTO;
import com.collab.platform.message.ws.WsMessageDispatcher;
import com.collab.platform.message.ws.WsSessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis Pub/Sub subscriber — receives cross-instance messages and delivers locally.
 *
 * <p>Deduplication is performed using SETNX to prevent double-delivery
 * when a message is both published and locally dispatched.</p>
 */
@Component
public class RedisMessageSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisMessageSubscriber.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final WsSessionManager wsSessionManager;
    private final WsMessageDispatcher wsMessageDispatcher;
    private final StringRedisTemplate stringRedisTemplate;

    public RedisMessageSubscriber(WsSessionManager wsSessionManager,
                                  WsMessageDispatcher wsMessageDispatcher,
                                  StringRedisTemplate stringRedisTemplate) {
        this.wsSessionManager = wsSessionManager;
        this.wsMessageDispatcher = wsMessageDispatcher;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody());
        WsMessageDTO wsMessage;
        try {
            wsMessage = OBJECT_MAPPER.readValue(body, WsMessageDTO.class);
        } catch (Exception e) {
            log.error("Failed to deserialize Redis message: {}", body, e);
            return;
        }

        // --- dedup check using SET NX EX ---
        String dedupKey = RedisKeyManager.DEDUP_PREFIX + wsMessage.getMessageId();
        Boolean isNew = stringRedisTemplate.opsForValue()
                .setIfAbsent(dedupKey, "1", 86400, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(isNew)) {
            log.debug("Duplicate message ignored: messageId={}", wsMessage.getMessageId());
            return;
        }

        // --- deliver locally if receiver is on this instance ---
        Long receiverId = wsMessage.getReceiverId();
        if (receiverId != null && wsSessionManager.hasSession(receiverId)) {
            wsMessageDispatcher.pushToSession(wsSessionManager.get(receiverId), wsMessage);
        }
        // If receiver is not local, skip — message has been persisted and will be
        // fetched as offline message when the receiver connects.
    }
}
