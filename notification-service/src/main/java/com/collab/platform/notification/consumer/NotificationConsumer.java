package com.collab.platform.notification.consumer;

import com.collab.platform.notification.config.RabbitMQConfig;
import com.collab.platform.notification.entity.Notification;
import com.collab.platform.notification.service.NotificationService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RabbitMQ consumer for domain events destined for notification processing.
 *
 * <p>Handles message.sent, file.uploaded, and user.joined events with
 * manual acknowledgment, Redis-based deduplication, and DLX-based retry.</p>
 */
@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private static final String DEDUP_PREFIX = "notif:dedup:";
    private static final long DEDUP_TTL_SECONDS = 86400L;
    private static final int MAX_RETRY_COUNT = 3;

    private final NotificationService notificationService;
    private final StringRedisTemplate stringRedisTemplate;

    public NotificationConsumer(NotificationService notificationService,
                                StringRedisTemplate stringRedisTemplate) {
        this.notificationService = notificationService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * Consume domain events from the notification queue.
     *
     * <p>The Jackson2JsonMessageConverter bean auto-deserializes JSON into
     * {@code Map<String, Object>}.</p>
     *
     * <p>Processing steps:
     * <ol>
     *   <li>Extract eventId, eventType, payload from the map</li>
     *   <li>Redis SET NX dedup check (TTL=86400s, P44)</li>
     *   <li>Build notification records based on eventType</li>
     *   <li>Batch insert to MySQL</li>
     *   <li>Manual ACK</li>
     * </ol>
     * On failure: extract x-death retry count; &lt;3 → requeue; ≥3 → DLX.</p>
     */
    @SuppressWarnings("unchecked")
    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE, ackMode = "MANUAL")
    public void onDomainEvent(Map<String, Object> eventMap,
                              Channel channel,
                              @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                              @Header(value = "x-death", required = false) List<Map<String, Object>> xDeath) {
        try {
            // 1. Extract event metadata
            String eventId = (String) eventMap.get("eventId");
            String eventType = (String) eventMap.get("eventType");

            if (eventId == null || eventType == null) {
                log.warn("Received event without eventId or eventType, discarding");
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 2. Dedup check via Redis SET NX (P44: TTL=86400s)
            String dedupKey = DEDUP_PREFIX + eventId;
            Boolean isNew = stringRedisTemplate.opsForValue()
                    .setIfAbsent(dedupKey, "1", Duration.ofSeconds(DEDUP_TTL_SECONDS));
            if (Boolean.FALSE.equals(isNew)) {
                log.debug("Duplicate event ignored: eventId={}", eventId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 3. Build notifications based on event type
            Map<String, Object> payload = (Map<String, Object>) eventMap.get("payload");
            List<Notification> notifications = new ArrayList<>();

            if ("message.sent".equals(eventType) && payload != null) {
                notifications = notificationService.buildFromMessageEvent(payload);
            } else if ("file.uploaded".equals(eventType) && payload != null) {
                Notification n = notificationService.buildFromFileEvent(payload);
                if (n != null) {
                    notifications.add(n);
                }
            } else {
                log.debug("Unhandled event type: {}, eventId={}", eventType, eventId);
            }

            // 4. Batch insert
            notificationService.batchInsert(notifications);

            // 5. ACK
            channel.basicAck(deliveryTag, false);
            log.info("Processed event: eventId={}, type={}, notificationsCreated={}",
                    eventId, eventType, notifications.size());

        } catch (Exception e) {
            log.error("Failed to process domain event, deliveryTag={}", deliveryTag, e);
            try {
                int retryCount = extractRetryCount(xDeath);
                if (retryCount < MAX_RETRY_COUNT) {
                    // Requeue for retry
                    channel.basicNack(deliveryTag, false, true);
                    log.warn("Event requeued for retry, deliveryTag={}, retryCount={}", deliveryTag, retryCount);
                } else {
                    // Max retries exhausted → route to DLX
                    channel.basicNack(deliveryTag, false, false);
                    log.error("Event sent to DLX after {} retries, deliveryTag={}", retryCount, deliveryTag);
                }
            } catch (IOException ackEx) {
                log.error("Failed to nack message, deliveryTag={}", deliveryTag, ackEx);
            }
        }
    }

    /**
     * Extract retry count from x-death header.
     * Returns the maximum count across all x-death entries.
     *
     * @param xDeath the x-death header from AMQP message
     * @return retry count, 0 if not found
     */
    private int extractRetryCount(List<Map<String, Object>> xDeath) {
        if (xDeath == null || xDeath.isEmpty()) {
            return 0;
        }
        int maxCount = 0;
        for (Map<String, Object> entry : xDeath) {
            Object countObj = entry.get("count");
            if (countObj instanceof Number n) {
                maxCount = Math.max(maxCount, n.intValue());
            }
        }
        return maxCount;
    }
}
