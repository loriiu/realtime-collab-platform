package com.collab.platform.message.controller;

import com.collab.platform.common.core.exception.BizException;
import com.collab.platform.common.core.result.Result;
import com.collab.platform.common.core.result.ResultCode;
import com.collab.platform.message.dto.WsMessageDTO;
import com.collab.platform.message.entity.Message;
import com.collab.platform.message.entity.Session;
import com.collab.platform.message.service.MessageService;
import com.collab.platform.message.service.OnlineService;
import com.collab.platform.message.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Session REST controller — provides session list, message history, offline messages, and online status.
 */
@RestController
@RequestMapping("/session")
public class SessionController {

    private static final Logger log = LoggerFactory.getLogger(SessionController.class);

    private final SessionService sessionService;
    private final MessageService messageService;
    private final OnlineService onlineService;

    public SessionController(SessionService sessionService,
                             MessageService messageService,
                             OnlineService onlineService) {
        this.sessionService = sessionService;
        this.messageService = messageService;
        this.onlineService = onlineService;
    }

    /**
     * GET /session/list — list all sessions for the current user.
     */
    @GetMapping("/list")
    public Result<List<Session>> listSessions(@RequestHeader("X-User-Id") Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "缺少认证信息");
        }
        List<Session> sessions = sessionService.listUserSessions(userId);
        return Result.success(sessions);
    }

    /**
     * GET /session/{sessionId}/messages — get paginated messages for a session.
     */
    @GetMapping("/{sessionId}/messages")
    public Result<List<WsMessageDTO>> getSessionMessages(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        List<WsMessageDTO> messages = messageService.getSessionMessages(sessionId, page, size);
        return Result.success(messages);
    }

    /**
     * GET /session/offline — get offline messages since a given timestamp.
     */
    @GetMapping("/offline")
    public Result<List<Message>> getOfflineMessages(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) Long lastMessageTime) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "缺少认证信息");
        }
        List<Message> messages = messageService.getOfflineMessages(userId, lastMessageTime);
        return Result.success(messages);
    }

    /**
     * GET /session/online-users — batch check online status for given user IDs.
     */
    @GetMapping("/online-users")
    public Result<Map<Long, Boolean>> getOnlineUsers(@RequestParam List<Long> userIds) {
        Map<Long, Boolean> statusMap = onlineService.batchCheckOnline(userIds);
        return Result.success(statusMap);
    }
}
