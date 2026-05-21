package com.collab.platform.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ configuration for notification-service — consumer side.
 *
 * <p>Declares the main notification queue, a dead-letter exchange/queue for
 * messages that exhaust retries, and the JSON message converter.</p>
 */
@Configuration
public class RabbitMQConfig {

    /** Dead-letter exchange name. */
    private static final String DLX_EXCHANGE = "collab.events.dlx";

    /** Notification queue name. */
    public static final String NOTIFICATION_QUEUE = "notification.queue";

    /** Dead-letter queue name. */
    public static final String NOTIFICATION_DLQ = "notification.dead-letter";

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
     * Declare the dead-letter exchange (topic, durable).
     *
     * @return DLX TopicExchange bean
     */
    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DLX_EXCHANGE, true, false);
    }

    /**
     * Declare the notification dead-letter queue.
     *
     * @return DLQ Queue bean
     */
    @Bean
    public Queue deadLetterQueue() {
        return new Queue(NOTIFICATION_DLQ, true);
    }

    /**
     * Bind the DLQ to the DLX with routing key "#".
     *
     * @return Binding bean
     */
    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with("#");
    }

    /**
     * Declare the main notification queue with DLX configured.
     * Messages that are rejected without requeue will be routed to the DLX.
     *
     * @return notification Queue bean
     */
    @Bean
    public Queue notificationQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", "notification.dead");
        return new Queue(NOTIFICATION_QUEUE, true, false, false, args);
    }

    /**
     * Bind notification.queue to collab.events for message.sent events.
     *
     * @return Binding bean
     */
    @Bean
    public Binding messageSentBinding() {
        return BindingBuilder.bind(notificationQueue())
                .to(collabEventsExchange())
                .with("message.sent");
    }

    /**
     * Bind notification.queue to collab.events for user.joined events.
     *
     * @return Binding bean
     */
    @Bean
    public Binding userJoinedBinding() {
        return BindingBuilder.bind(notificationQueue())
                .to(collabEventsExchange())
                .with("user.joined");
    }

    /**
     * Bind notification.queue to collab.events for file.uploaded events.
     *
     * @return Binding bean
     */
    @Bean
    public Binding fileUploadedBinding() {
        return BindingBuilder.bind(notificationQueue())
                .to(collabEventsExchange())
                .with("file.uploaded");
    }

    /**
     * Jackson-based JSON message converter for deserializing DomainEventDTO
     * on the consumer side.
     *
     * @return Jackson2JsonMessageConverter bean
     */
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
