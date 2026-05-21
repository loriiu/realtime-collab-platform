package com.collab.platform.file.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for file-service — producer side.
 *
 * <p>Declares the collab.events topic exchange and a JSON message converter
 * for publishing file.uploaded domain events.</p>
 */
@Configuration
public class RabbitMQConfig {

    /**
     * Declare the collab.events topic exchange (durable, non-auto-delete).
     *
     * @return TopicExchange bean
     */
    @Bean
    public TopicExchange collabEventsExchange() {
        return new TopicExchange("collab.events", true, false);
    }

    /**
     * Jackson-based JSON message converter for serializing DomainEventDTO
     * on the producer side.
     *
     * @return Jackson2JsonMessageConverter bean
     */
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
