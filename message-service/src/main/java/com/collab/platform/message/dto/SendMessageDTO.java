package com.collab.platform.message.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * Inbound DTO for sending a chat message via WebSocket.
 */
@Data
public class SendMessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Target session ID. */
    private Long sessionId;

    /** Target receiver user ID. */
    private Long receiverId;

    /** Message content (plain text or serialized rich content). */
    private String content;

    /** Message type, defaults to "TEXT". */
    private String type;
}
