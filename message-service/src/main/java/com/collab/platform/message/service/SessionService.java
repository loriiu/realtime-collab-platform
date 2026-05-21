package com.collab.platform.message.service;

import cn.hutool.core.lang.Snowflake;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.collab.platform.message.entity.Session;
import com.collab.platform.message.mapper.SessionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Session management — create and query chat sessions between two users.
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final SessionMapper sessionMapper;
    private final Snowflake snowflake;

    public SessionService(SessionMapper sessionMapper, Snowflake snowflake) {
        this.sessionMapper = sessionMapper;
        this.snowflake = snowflake;
    }

    /**
     * Get or create a session between two users.
     * The session key is built by sorting both user IDs to ensure uniqueness.
     *
     * @param userA first user ID
     * @param userB second user ID
     * @return the existing or newly created session
     */
    public Session getOrCreateSession(Long userA, Long userB) {
        // Build deterministic session key: smaller ID first
        long first = Math.min(userA, userB);
        long second = Math.max(userA, userB);
        String sessionKey = first + ":" + second;

        // Try to find existing session
        Session existing = sessionMapper.selectOne(
                new LambdaQueryWrapper<Session>().eq(Session::getSessionKey, sessionKey));
        if (existing != null) {
            return existing;
        }

        // Create new session
        Session session = new Session();
        session.setId(snowflake.nextId());
        session.setSessionKey(sessionKey);
        session.setUserA(first);
        session.setUserB(second);
        session.setCreateTime(LocalDateTime.now());
        sessionMapper.insert(session);

        log.info("Session created: id={}, key={}", session.getId(), sessionKey);
        return session;
    }

    /**
     * List all sessions that the user participates in.
     *
     * @param userId the user ID
     * @return list of sessions, ordered by last message time descending
     */
    public List<Session> listUserSessions(Long userId) {
        return sessionMapper.selectList(
                new LambdaQueryWrapper<Session>()
                        .and(wrapper -> wrapper
                                .eq(Session::getUserA, userId)
                                .or()
                                .eq(Session::getUserB, userId))
                        .orderByDesc(Session::getLastMessageTime)
        );
    }
}
