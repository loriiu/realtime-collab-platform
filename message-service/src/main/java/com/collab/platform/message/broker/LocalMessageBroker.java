package com.collab.platform.message.broker;

import com.collab.platform.message.dto.WsMessageDTO;
import com.collab.platform.message.redis.RedisMessageSubscriber;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Local message broker — used when Redis Pub/Sub is unavailable (single-instance mode).
 * Messages are delivered locally via {@code WsMessageDispatcher} in the handler.
 */
@Component
@ConditionalOnMissingBean(RedisMessageSubscriber.class)
public class LocalMessageBroker implements MessageBroker {

    @Override
    public void publish(WsMessageDTO message) {
        // No-op: in single-instance mode, the message has already been delivered
        // locally by WsMessageDispatcher.dispatch() called from ChatWebSocketHandler.
    }
}
