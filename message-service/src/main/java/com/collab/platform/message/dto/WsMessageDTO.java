package com.collab.platform.message.dto;

import com.collab.platform.message.entity.Message;
import com.collab.platform.message.feign.UserBriefDTO;
import lombok.Data;

import java.io.Serializable;

/**
 * Outbound WebSocket message DTO — the wire format for real-time messaging.
 */
@Data
public class WsMessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Message protocol version, default 1. */
    private int version;

    /** Message type: CHAT, ACK, SYSTEM, READ, ERROR. */
    private String type;

    /** Unique message ID (Snowflake). */
    private Long messageId;

    /** Session ID. */
    private Long sessionId;

    /** Sender user ID. */
    private Long senderId;

    /** Receiver user ID. */
    private Long receiverId;

    /** Sender display name (populated from user-service). */
    private String senderNickname;

    /** Sender avatar URL (populated from user-service). */
    private String senderAvatar;

    /** Message content. */
    private String content;

    /** Unix timestamp in milliseconds. */
    private Long timestamp;

    /** Delivery status: 0=sending, 1=delivered, 2=read. */
    private Integer status;

    // --- factory methods ---

    /**
     * Create a CHAT message DTO from a persisted Message entity and sender info.
     *
     * @param msg    the persisted message
     * @param sender the sender's brief info (from user-service)
     * @return populated WsMessageDTO
     */
    public static WsMessageDTO fromMessage(Message msg, UserBriefDTO sender) {
        WsMessageDTO dto = new WsMessageDTO();
        dto.setVersion(msg.getVersion() != null ? msg.getVersion() : 1);
        dto.setType("CHAT");
        dto.setMessageId(msg.getId());
        dto.setSessionId(msg.getSessionId());
        dto.setSenderId(msg.getSenderId());
        dto.setReceiverId(msg.getReceiverId());
        dto.setContent(msg.getContent());
        dto.setTimestamp(System.currentTimeMillis());
        dto.setStatus(msg.getStatus() != null ? msg.getStatus() : 0);
        if (sender != null) {
            dto.setSenderNickname(sender.getNickname());
            dto.setSenderAvatar(sender.getAvatar());
        }
        return dto;
    }

    /**
     * Create an ACK message for delivery/read confirmation.
     *
     * @param messageId the acknowledged message ID
     * @param status    1=delivered, 2=read
     * @return ACK WsMessageDTO
     */
    public static WsMessageDTO ack(Long messageId, int status) {
        WsMessageDTO dto = new WsMessageDTO();
        dto.setVersion(1);
        dto.setType("ACK");
        dto.setMessageId(messageId);
        dto.setStatus(status);
        dto.setTimestamp(System.currentTimeMillis());
        return dto;
    }

    /**
     * Create an ERROR message.
     *
     * @param content the error description
     * @return ERROR WsMessageDTO
     */
    public static WsMessageDTO error(String content) {
        WsMessageDTO dto = new WsMessageDTO();
        dto.setVersion(1);
        dto.setType("ERROR");
        dto.setContent(content);
        dto.setTimestamp(System.currentTimeMillis());
        return dto;
    }
}
