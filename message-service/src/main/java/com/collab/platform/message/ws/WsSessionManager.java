package com.collab.platform.message.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe manager for WebSocket sessions.
 * Maps user IDs to their active WebSocket sessions.
 */
@Component
public class WsSessionManager {

    private static final Logger log = LoggerFactory.getLogger(WsSessionManager.class);

    private final ConcurrentHashMap<Long, WebSocketSession> sessionMap = new ConcurrentHashMap<>();

    /**
     * Register or replace the WebSocket session for a user.
     * If an old session exists for the same user, it is closed and replaced.
     *
     * @param userId  the user ID
     * @param session the new WebSocket session
     */
    public void put(Long userId, WebSocketSession session) {
        WebSocketSession old = sessionMap.put(userId, session);
        if (old != null && old.isOpen()) {
            try {
                old.close();
            } catch (Exception e) {
                log.warn("Failed to close old session for userId={}", userId, e);
            }
        }
        log.debug("Session registered: userId={}, sessionId={}", userId, session.getId());
    }

    /**
     * Remove the session for a user.
     *
     * @param userId the user ID
     */
    public void remove(Long userId) {
        sessionMap.remove(userId);
        log.debug("Session removed: userId={}", userId);
    }

    /**
     * Get the active WebSocket session for a user.
     *
     * @param userId the user ID
     * @return the session, or null if not connected
     */
    public WebSocketSession get(Long userId) {
        return sessionMap.get(userId);
    }

    /**
     * Check if a user has an active local session.
     *
     * @param userId the user ID
     * @return true if the user has an active session on this instance
     */
    public boolean hasSession(Long userId) {
        WebSocketSession session = sessionMap.get(userId);
        return session != null && session.isOpen();
    }

    /**
     * Get all currently connected user IDs.
     *
     * @return set of user IDs with active sessions
     */
    public Set<Long> getAllUserIds() {
        return sessionMap.keySet();
    }
}
