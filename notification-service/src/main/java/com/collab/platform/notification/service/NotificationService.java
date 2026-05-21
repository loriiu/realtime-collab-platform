package com.collab.platform.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.collab.platform.common.core.exception.BizException;
import com.collab.platform.notification.dto.NotificationDTO;
import com.collab.platform.notification.entity.Notification;
import com.collab.platform.notification.mapper.NotificationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Notification business logic: build from events, query, mark read.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final NotificationMapper notificationMapper;

    public NotificationService(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    /**
     * Build notification records from a "message.sent" domain event.
     * Creates one notification per receiver in the event payload.
     *
     * @param eventPayload the domain event payload map
     * @return list of Notification entities to persist
     */
    @SuppressWarnings("unchecked")
    public List<Notification> buildFromMessageEvent(Map<String, Object> eventPayload) {
        List<Notification> notifications = new ArrayList<>();

        String messageId = (String) eventPayload.get("messageId");
        String sessionId = (String) eventPayload.get("sessionId");
        String preview = (String) eventPayload.getOrDefault("preview", "");

        Object receiverIdsObj = eventPayload.get("receiverIds");
        if (!(receiverIdsObj instanceof List<?> receiverIds)) {
            log.warn("message.sent event missing receiverIds, skipping notification build");
            return notifications;
        }

        for (Object rid : receiverIds) {
            Long receiverId = toLong(rid);
            if (receiverId == null) {
                continue;
            }
            Notification n = new Notification();
            n.setUserId(receiverId);
            n.setType("MESSAGE");
            n.setTitle("新消息");
            n.setContent(preview);
            n.setSourceId(messageId);
            n.setSourceType("message");
            n.setIsRead(0);
            n.setCreateTime(LocalDateTime.now());
            notifications.add(n);
        }
        return notifications;
    }

    /**
     * Build a notification record from a "file.uploaded" domain event.
     *
     * @param eventPayload the domain event payload map
     * @return single Notification entity, or null if data insufficient
     */
    public Notification buildFromFileEvent(Map<String, Object> eventPayload) {
        String fileId = (String) eventPayload.get("fileId");
        String fileName = (String) eventPayload.getOrDefault("fileName", "未知文件");

        Notification n = new Notification();
        // file.uploaded events may have uploaderId, we notify the uploader as confirmation
        Object uploaderIdObj = eventPayload.get("uploaderId");
        if (uploaderIdObj == null) {
            log.warn("file.uploaded event missing uploaderId, skipping notification");
            return null;
        }
        Long uploaderId = toLong(uploaderIdObj);
        if (uploaderId == null) {
            return null;
        }

        n.setUserId(uploaderId);
        n.setType("FILE_UPLOADED");
        n.setTitle("文件上传成功");
        n.setContent("文件 " + fileName + " 已成功上传");
        n.setSourceId(fileId);
        n.setSourceType("file");
        n.setIsRead(0);
        n.setCreateTime(LocalDateTime.now());
        return n;
    }

    /**
     * Batch insert notifications.
     *
     * @param notifications list of Notification entities
     */
    public void batchInsert(List<Notification> notifications) {
        if (notifications.isEmpty()) {
            return;
        }
        for (Notification n : notifications) {
            try {
                notificationMapper.insert(n);
            } catch (Exception e) {
                log.error("Failed to insert notification for userId={}", n.getUserId(), e);
            }
        }
    }

    /**
     * Cursor-based pagination query for a user's notifications.
     *
     * @param userId the target user ID
     * @param isRead read filter: null=all, 0=unread, 1=read
     * @param lastId cursor (last seen notification ID), null for first page
     * @param limit  page size (clamped to [1, MAX_LIMIT])
     * @return list of NotificationDTO
     */
    public List<NotificationDTO> list(Long userId, Integer isRead, Long lastId, int limit) {
        int effectiveLimit = Math.min(Math.max(limit > 0 ? limit : DEFAULT_LIMIT, 1), MAX_LIMIT);

        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId);

        if (isRead != null) {
            wrapper.eq(Notification::getIsRead, isRead);
        }
        if (lastId != null && lastId > 0) {
            wrapper.lt(Notification::getId, lastId);
        }

        wrapper.orderByDesc(Notification::getId)
                .last("LIMIT " + effectiveLimit);

        List<Notification> notifications = notificationMapper.selectList(wrapper);
        return notifications.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * Mark a single notification as read.
     *
     * @param id     notification ID
     * @param userId the owner user ID (for security)
     * @throws BizException if notification not found or not owned by user
     */
    public void markRead(Long id, Long userId) {
        Notification notification = notificationMapper.selectById(id);
        if (notification == null || !notification.getUserId().equals(userId)) {
            throw new BizException(403, "无权操作");
        }
        LambdaUpdateWrapper<Notification> wrapper = new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, userId)
                .set(Notification::getIsRead, 1);
        notificationMapper.update(null, wrapper);
    }

    /**
     * Mark all notifications as read for a user.
     *
     * @param userId the owner user ID
     * @return number of notifications marked as read
     */
    public int markAllRead(Long userId) {
        LambdaUpdateWrapper<Notification> wrapper = new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1);
        return notificationMapper.update(null, wrapper);
    }

    /**
     * Get the unread notification count for a user.
     *
     * @param userId the owner user ID
     * @return count of unread notifications
     */
    public long getUnreadCount(Long userId) {
        return notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0));
    }

    /**
     * Convert entity to DTO.
     */
    private NotificationDTO toDTO(Notification n) {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(n.getId());
        dto.setUserId(n.getUserId());
        dto.setType(n.getType());
        dto.setTitle(n.getTitle());
        dto.setContent(n.getContent());
        dto.setSourceId(n.getSourceId());
        dto.setSourceType(n.getSourceType());
        dto.setIsRead(n.getIsRead());
        dto.setCreateTime(n.getCreateTime());
        return dto;
    }

    /**
     * Safely convert an Object to Long.
     */
    private Long toLong(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
