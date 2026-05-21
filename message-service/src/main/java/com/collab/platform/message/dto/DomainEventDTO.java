package com.collab.platform.message.dto;

import com.collab.platform.message.dto.WsMessageDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Unified domain event envelope for RabbitMQ message bus.
 *
 * <p>All services publish events using this envelope format, and consumers
 * deserialize it to dispatch to their internal handlers.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DomainEventDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Unique event ID (UUID). */
    private String eventId;

    /** Event type: "message.sent" | "file.uploaded" | "user.joined". */
    private String eventType;

    /** Event occurred epoch milliseconds. */
    private Long occurredAt;

    /** Publisher identifier, e.g. "message-service-1". */
    private String publisherId;

    /** Event-specific payload (flexible key-value map). */
    private Map<String, Object> payload;

    /**
     * Build a "message.sent" domain event from a WebSocket message DTO.
     *
     * @param msg         the outbound WsMessageDTO
     * @param publisherId the publisher service instance ID
     * @return populated DomainEventDTO
     */
    public static DomainEventDTO fromMessage(WsMessageDTO msg, String publisherId) {
        DomainEventDTO event = new DomainEventDTO();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType("message.sent");
        event.setOccurredAt(System.currentTimeMillis());
        event.setPublisherId(publisherId);

        Map<String, Object> p = new HashMap<>();
        p.put("messageId", String.valueOf(msg.getMessageId()));
        p.put("sessionId", String.valueOf(msg.getSessionId()));
        p.put("senderId", msg.getSenderId());
        p.put("receiverIds", List.of(msg.getReceiverId()));
        p.put("contentType", "text");

        String content = msg.getContent();
        p.put("preview", content != null && content.length() > 50
                ? content.substring(0, 50) : content);

        event.setPayload(p);
        return event;
    }
}
