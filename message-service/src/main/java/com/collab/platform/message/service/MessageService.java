package com.collab.platform.message.service;

import cn.hutool.core.lang.Snowflake;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.collab.platform.common.core.result.Result;
import com.collab.platform.message.dto.SendMessageDTO;
import com.collab.platform.message.dto.WsMessageDTO;
import com.collab.platform.message.entity.Message;
import com.collab.platform.message.feign.UserBriefDTO;
import com.collab.platform.message.feign.UserFeignClient;
import com.collab.platform.message.mapper.MessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * Core message business logic: send, query, update status.
 */
@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final MessageMapper messageMapper;
    private final Snowflake snowflake;
    private final UserFeignClient userFeignClient;
    private final ExecutorService messagePersistenceExecutor;

    @Value("${server-id:msg-1}")
    private String serverId;

    public MessageService(MessageMapper messageMapper,
                          Snowflake snowflake,
                          UserFeignClient userFeignClient,
                          @Qualifier("messagePersistenceExecutor") ExecutorService messagePersistenceExecutor) {
        this.messageMapper = messageMapper;
        this.snowflake = snowflake;
        this.userFeignClient = userFeignClient;
        this.messagePersistenceExecutor = messagePersistenceExecutor;
    }

    /**
     * Return the server instance identifier.
     *
     * @return server ID (e.g. "msg-1", "msg-2")
     */
    public String getServerId() {
        return serverId;
    }

    /**
     * Send a message: generate ID → build entity → async persist → build DTO with sender info.
     *
     * @param dto      the send request
     * @param senderId the authenticated sender ID
     * @return the WsMessageDTO ready for dispatch
     */
    public WsMessageDTO sendMessage(SendMessageDTO dto, Long senderId) {
        // Generate Snowflake ID
        long messageId = snowflake.nextId();

        // Build entity
        Message message = new Message();
        message.setId(messageId);
        message.setSessionId(dto.getSessionId());
        message.setSenderId(senderId);
        message.setReceiverId(dto.getReceiverId());
        message.setContent(dto.getContent());
        message.setType(dto.getType() != null ? dto.getType() : "TEXT");
        message.setVersion(1);
        message.setStatus(0);
        message.setIsDeleted(0);
        message.setCreateTime(LocalDateTime.now());

        // Async persist (non-blocking WebSocket IO thread)
        CompletableFuture.runAsync(() -> {
            try {
                messageMapper.insert(message);
                log.debug("Message persisted: id={}", messageId);
            } catch (DuplicateKeyException e) {
                log.debug("Duplicate message insert (idempotent), id={}", messageId);
            } catch (Exception e) {
                log.error("Failed to persist message id={}", messageId, e);
            }
        }, messagePersistenceExecutor);

        // Fetch sender info for the outbound DTO
        UserBriefDTO sender = null;
        try {
            Result<UserBriefDTO> result = userFeignClient.getUserInfo(senderId);
            if (result != null && result.getCode() == 200) {
                sender = result.getData();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch sender info for userId={}, using defaults", senderId);
        }

        return WsMessageDTO.fromMessage(message, sender);
    }

    /**
     * Get offline messages for a user since a given timestamp.
     *
     * @param userId          the receiver user ID
     * @param lastMessageTime the cutoff time (milliseconds since epoch)
     * @return list of messages
     */
    public List<Message> getOfflineMessages(Long userId, Long lastMessageTime) {
        LocalDateTime since;
        if (lastMessageTime != null && lastMessageTime > 0) {
            since = LocalDateTime.ofEpochSecond(
                    lastMessageTime / 1000,
                    (int) ((lastMessageTime % 1000) * 1_000_000),
                    java.time.ZoneOffset.of("+8"));
        } else {
            // Default: last 24 hours
            since = LocalDateTime.now().minusHours(24);
        }

        return messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getReceiverId, userId)
                        .eq(Message::getIsDeleted, 0)
                        .gt(Message::getCreateTime, since)
                        .orderByAsc(Message::getCreateTime)
        );
    }

    /**
     * Update the delivery status of a message.
     *
     * @param messageId the message ID
     * @param status    new status (1=delivered, 2=read)
     */
    public void updateStatus(Long messageId, Integer status) {
        Message update = new Message();
        update.setId(messageId);
        update.setStatus(status);
        messageMapper.updateById(update);
    }

    /**
     * Get paginated messages for a session, with sender nicknames/avatars populated.
     *
     * @param sessionId the session ID
     * @param page      page number (1-based)
     * @param size      page size
     * @return list of WsMessageDTO with sender info
     */
    public List<WsMessageDTO> getSessionMessages(Long sessionId, int page, int size) {
        Page<Message> pageObj = new Page<>(page, size);
        Page<Message> result = messageMapper.selectPage(pageObj,
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getSessionId, sessionId)
                        .eq(Message::getIsDeleted, 0)
                        .orderByDesc(Message::getCreateTime)
        );

        List<Message> messages = result.getRecords();
        if (messages.isEmpty()) {
            return Collections.emptyList();
        }

        // Collect unique sender IDs
        List<Long> senderIds = messages.stream()
                .map(Message::getSenderId)
                .distinct()
                .collect(Collectors.toList());

        // Batch query sender info from user-service
        Map<Long, UserBriefDTO> userMap = Collections.emptyMap();
        try {
            Result<Map<Long, UserBriefDTO>> batchResult = userFeignClient.batchGetUserInfo(senderIds);
            if (batchResult != null && batchResult.getCode() == 200 && batchResult.getData() != null) {
                userMap = batchResult.getData();
            }
        } catch (Exception e) {
            log.warn("Failed to batch fetch user info for senderIds={}", senderIds, e);
        }

        // Build DTOs with sender info
        List<WsMessageDTO> dtos = new ArrayList<>(messages.size());
        for (Message msg : messages) {
            UserBriefDTO sender = userMap.get(msg.getSenderId());
            dtos.add(WsMessageDTO.fromMessage(msg, sender));
        }
        return dtos;
    }
}
