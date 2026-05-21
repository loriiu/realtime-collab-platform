package com.collab.platform.message.broker;

import com.collab.platform.message.dto.WsMessageDTO;
import com.collab.platform.message.redis.RedisMessagePublisher;
import com.collab.platform.message.redis.RedisMessageSubscriber;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub message broker — used in multi-instance deployments.
 * Publishes messages to Redis channel for cross-instance delivery.
 */
@Component
@ConditionalOnBean(RedisMessageSubscriber.class)
@Primary
public class RedisPubSubMessageBroker implements MessageBroker {

    private static final String CHANNEL = "collab:msg:chat";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RedisMessagePublisher publisher;

    public RedisPubSubMessageBroker(RedisMessagePublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(WsMessageDTO message) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(message);
            publisher.publish(CHANNEL, json);
        } catch (Exception e) {
            // Serialization failure — log and skip
        }
    }
}
