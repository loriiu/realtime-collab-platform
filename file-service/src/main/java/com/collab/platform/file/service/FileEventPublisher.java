package com.collab.platform.file.service;

import com.collab.platform.file.entity.FileRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes file.uploaded domain events to RabbitMQ.
 *
 * <p>Uses publisher confirms with CorrelationData(eventId) and logs
 * NACK warnings.</p>
 */
@Component
public class FileEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(FileEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public FileEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Publish a "file.uploaded" event to the collab.events topic exchange.
     *
     * @param record the persisted file record
     */
    public void publishFileUploaded(FileRecord record) {
        String eventId = UUID.randomUUID().toString();

        Map<String, Object> payload = new HashMap<>();
        payload.put("fileId", record.getFileId());
        payload.put("fileName", record.getOriginalName());
        payload.put("objectKey", record.getObjectKey());
        payload.put("bucket", record.getBucket());
        payload.put("contentType", record.getContentType());
        payload.put("sizeBytes", record.getSizeBytes());
        payload.put("uploaderId", record.getUploaderId());

        Map<String, Object> event = new HashMap<>();
        event.put("eventId", eventId);
        event.put("eventType", "file.uploaded");
        event.put("occurredAt", System.currentTimeMillis());
        event.put("publisherId", "file-service");
        event.put("payload", payload);

        CorrelationData cd = new CorrelationData(eventId);
        rabbitTemplate.convertAndSend("collab.events", "file.uploaded", event, cd);
        cd.getFuture().whenComplete((confirm, ex) -> {
            if (ex != null || (confirm != null && !confirm.isAck())) {
                log.warn("Publisher NACK for file.uploaded eventId={}", eventId);
            }
        });
    }
}
