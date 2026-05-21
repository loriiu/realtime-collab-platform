package com.collab.platform.message.ws;

import com.collab.platform.message.broker.MessageBroker;
import com.collab.platform.message.dto.DomainEventDTO;
import com.collab.platform.message.dto.SendMessageDTO;
import com.collab.platform.message.dto.WsMessageDTO;
import com.collab.platform.message.event.DomainEventPublisher;
import com.collab.platform.message.redis.MessageRateLimiter;
import com.collab.platform.message.service.MessageService;
import com.collab.platform.message.service.OnlineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Core WebSocket handler for real-time chat messaging.
 *
 * <p>Lifecycle: connect → authenticate → rate-limit → persist → dispatch → broadcast
 * → publish domain event.</p>
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final WsSessionManager wsSessionManager;
    private final WsMessageDispatcher wsMessageDispatcher;
    private final OnlineService onlineService;
    private final MessageService messageService;
    private final MessageRateLimiter messageRateLimiter;
    private final MessageBroker messageBroker;
    private final DomainEventPublisher domainEventPublisher;

    public ChatWebSocketHandler(WsSessionManager wsSessionManager,
                                WsMessageDispatcher wsMessageDispatcher,
                                OnlineService onlineService,
                                MessageService messageService,
                                MessageRateLimiter messageRateLimiter,
                                MessageBroker messageBroker,
                                DomainEventPublisher domainEventPublisher) {
        this.wsSessionManager = wsSessionManager;
        this.wsMessageDispatcher = wsMessageDispatcher;
        this.onlineService = onlineService;
        this.messageService = messageService;
        this.messageRateLimiter = messageRateLimiter;
        this.messageBroker = messageBroker;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            log.warn("WebSocket connected without userId in attributes, closing");
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (Exception ignored) {
            }
            return;
        }

        wsSessionManager.put(userId, session);
        onlineService.markOnline(userId);
        log.info("WebSocket connected: userId={}, sessionId={}", userId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws Exception {
        Long senderId = (Long) session.getAttributes().get("userId");
        if (senderId == null) {
            sendError(session, "未认证的连接");
            return;
        }

        String payload = textMessage.getPayload();

        // --- heartbeat ping/pong ---
        if (payload.contains("\"type\":\"PING\"")) {
            session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
            onlineService.refreshHeartbeat(senderId);
            return;
        }

        // --- rate limit check ---
        if (!messageRateLimiter.tryAcquire(senderId)) {
            sendError(session, "发送消息过于频繁，请稍后再试");
            return;
        }

        // --- parse message ---
        SendMessageDTO sendDto;
        try {
            sendDto = OBJECT_MAPPER.readValue(payload, SendMessageDTO.class);
        } catch (Exception e) {
            sendError(session, "消息格式错误");
            return;
        }

        if (sendDto.getContent() == null || sendDto.getContent().isBlank()) {
            sendError(session, "消息内容不能为空");
            return;
        }

        // --- persist & build outbound DTO (persistence is async inside sendMessage) ---
        final Long finalSenderId = senderId;
        WsMessageDTO wsMessage = messageService.sendMessage(sendDto, finalSenderId);

        // --- Server ACK: confirm message persisted to sender ---
        sendAck(session, wsMessage.getMessageId(), 0);

        // --- publish message.sent event to RabbitMQ (after persist + ACK) ---
        domainEventPublisher.publishMessageSent(
                DomainEventDTO.fromMessage(wsMessage, messageService.getServerId()));

        // --- dispatch: try local session first ---
        wsMessageDispatcher.dispatch(wsMessage);

        // --- broadcast to other instances via broker ---
        messageBroker.publish(wsMessage);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Long userId = (Long) session.getAttributes().get("userId");
        log.error("WebSocket transport error: userId={}, sessionId={}", userId, session.getId(), exception);
        if (userId != null) {
            wsSessionManager.remove(userId);
            onlineService.markOffline(userId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            wsSessionManager.remove(userId);
            onlineService.markOffline(userId);
        }
        log.info("WebSocket disconnected: userId={}, sessionId={}, status={}", userId, session.getId(), status);
    }

    private void sendError(WebSocketSession session, String content) {
        try {
            WsMessageDTO errorMsg = WsMessageDTO.error(content);
            session.sendMessage(new TextMessage(OBJECT_MAPPER.writeValueAsString(errorMsg)));
        } catch (Exception e) {
            log.error("Failed to send error message", e);
        }
    }

    private void sendAck(WebSocketSession session, Long messageId, int status) {
        try {
            WsMessageDTO ack = WsMessageDTO.ack(messageId, status);
            session.sendMessage(new TextMessage(OBJECT_MAPPER.writeValueAsString(ack)));
        } catch (Exception e) {
            log.error("Failed to send ACK for messageId={}", messageId, e);
        }
    }
}
