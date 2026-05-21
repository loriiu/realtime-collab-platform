package com.collab.platform.message.ws;

import com.collab.platform.message.dto.WsMessageDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Dispatches WS messages: checks local session first, then falls back to cross-instance.
 */
@Component
public class WsMessageDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WsMessageDispatcher.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final WsSessionManager wsSessionManager;

    public WsMessageDispatcher(WsSessionManager wsSessionManager) {
        this.wsSessionManager = wsSessionManager;
    }

    /**
     * Dispatch a message to its receiver. Checks local session first.
     * If the receiver is not connected locally, the message has already been
     * published to other instances via the MessageBroker.
     *
     * @param message the message to dispatch
     */
    public void dispatch(WsMessageDTO message) {
        Long receiverId = message.getReceiverId();
        if (receiverId == null) {
            log.warn("Cannot dispatch message: receiverId is null, messageId={}", message.getMessageId());
            return;
        }

        WebSocketSession receiverSession = wsSessionManager.get(receiverId);
        if (receiverSession != null && receiverSession.isOpen()) {
            pushToSession(receiverSession, message);
        }
        // If receiver is not on this instance, the message has been / will be
        // delivered via Redis Pub/Sub broadcast to the correct instance.
    }

    /**
     * Push a message to a specific WebSocket session.
     *
     * @param session the target session
     * @param message the message to send
     */
    public void pushToSession(WebSocketSession session, WsMessageDTO message) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("Failed to push message to session {}: {}", session.getId(), e.getMessage());
        }
    }
}
