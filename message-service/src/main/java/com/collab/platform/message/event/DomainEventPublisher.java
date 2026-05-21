package com.collab.platform.message.event;

import com.collab.platform.message.dto.DomainEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes domain events to the collab.events topic exchange via RabbitMQ.
 *
 * <p>Uses publisher confirms with CorrelationData(eventId) and logs NACK
 * warnings so the sender is aware of failed deliveries.</p>
 */
@Component
public class DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public DomainEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Publish a "message.sent" event to the collab.events exchange.
     *
     * <p>The CorrelationData carries the eventId so that publisher-confirm
     * callbacks can identify which event was (N)ACKed.</p>
     *
     * @param event the domain event to publish
     */
    public void publishMessageSent(DomainEventDTO event) {
        CorrelationData cd = new CorrelationData(event.getEventId());
        rabbitTemplate.convertAndSend("collab.events", "message.sent", event, cd);
        cd.getFuture().whenComplete((confirm, ex) -> {
            if (ex != null || (confirm != null && !confirm.isAck())) {
                log.warn("Publisher NACK for message.sent eventId={}", event.getEventId());
            }
        });
    }
}
