package com.collab.platform.message.broker;

import com.collab.platform.message.dto.WsMessageDTO;

/**
 * Message broker abstraction for cross-instance message broadcasting.
 * <p>
 * In single-instance mode, messages are delivered locally via {@link com.collab.platform.message.ws.WsMessageDispatcher}.
 * In multi-instance mode, messages are published to Redis Pub/Sub for cross-instance delivery.
 * </p>
 */
public interface MessageBroker {

    /**
     * Publish a message for cross-instance delivery.
     * The local dispatch is handled separately by {@code WsMessageDispatcher}.
     *
     * @param message the message to broadcast
     */
    void publish(WsMessageDTO message);
}
