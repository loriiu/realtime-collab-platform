package com.collab.platform.message.config;

import com.collab.platform.message.redis.RedisMessageSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis Pub/Sub configuration — subscribes to cross-instance message channel.
 */
@Configuration
public class RedisPubSubConfig {

    private final RedisMessageSubscriber redisMessageSubscriber;

    public RedisPubSubConfig(RedisMessageSubscriber redisMessageSubscriber) {
        this.redisMessageSubscriber = redisMessageSubscriber;
    }

    /**
     * Register the message listener container, subscribing to the chat channel.
     *
     * @param connectionFactory Redis connection factory
     * @return configured listener container
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(redisMessageSubscriber,
                new PatternTopic("collab:msg:chat"));
        return container;
    }
}
