package com.collab.platform.message.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * RabbitMQ configuration for message-service — producer side.
 *
 * <p>Declares the collab.events topic exchange, configures a RabbitTemplate
 * with JSON converter and publisher confirms, and registers a Micrometer
 * gauge for the async executor queue depth.</p>
 */
@Configuration
public class RabbitMQConfig {

    @Value("${spring.rabbitmq.host:localhost}")
    private String host;

    @Value("${spring.rabbitmq.port:5672}")
    private int port;

    @Value("${spring.rabbitmq.username:guest}")
    private String username;

    @Value("${spring.rabbitmq.password:guest}")
    private String password;

    /**
     * RabbitMQ connection factory with publisher confirms enabled (CORRELATED mode).
     *
     * @return the connection factory
     */
    @Bean
    public ConnectionFactory connectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory(host, port);
        factory.setUsername(username);
        factory.setPassword(password);
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        return factory;
    }

    /**
     * RabbitTemplate configured with JSON message converter for domain events.
     *
     * @param connectionFactory the RabbitMQ connection factory
     * @return configured RabbitTemplate
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        return template;
    }

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
     * P38: Micrometer gauge for async executor queue depth.
     * Monitors the messagePersistenceExecutor queue size to detect back-pressure.
     *
     * @param executor the message persistence thread pool
     * @return MeterBinder registered with the global registry
     */
    @Bean
    public MeterBinder executorQueueGauge(
            @Qualifier("messagePersistenceExecutor") ExecutorService executor) {
        return registry -> {
            if (executor instanceof ThreadPoolExecutor tpe) {
                Gauge.builder("async.executor.queue.size", tpe, e -> e.getQueue().size())
                        .description("WebSocket async executor queue depth")
                        .register(registry);
            }
        };
    }
}
