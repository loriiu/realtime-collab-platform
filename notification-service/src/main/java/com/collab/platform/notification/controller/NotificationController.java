package com.collab.platform.notification.controller;

import com.collab.platform.common.core.result.Result;
import com.collab.platform.notification.dto.NotificationDTO;
import com.collab.platform.notification.service.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for notification queries and read status management.
 */
@RestController
@RequestMapping("/notification")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * List notifications for the authenticated user with cursor-based pagination.
     *
     * @param userId the authenticated user ID (from gateway header)
     * @param isRead optional read filter: 0=unread, 1=read, null=all
     * @param lastId cursor (last seen notification ID), null for first page
     * @param limit  page size, default 20
     * @return notification list with unread count
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(value = "isRead", required = false) Integer isRead,
            @RequestParam(value = "lastId", required = false) Long lastId,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        List<NotificationDTO> notifications = notificationService.list(userId, isRead, lastId, limit);
        long unreadCount = notificationService.getUnreadCount(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("list", notifications);
        result.put("unreadCount", unreadCount);
        result.put("hasMore", notifications.size() >= limit);
        return Result.success(result);
    }

    /**
     * Mark a single notification as read.
     *
     * @param userId       the authenticated user ID
     * @param notificationId the notification ID to mark as read
     * @return success result
     */
    @PostMapping("/read/{id}")
    public Result<Void> markRead(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable("id") Long notificationId) {
        notificationService.markRead(notificationId, userId);
        return Result.success();
    }

    /**
     * Mark all unread notifications as read for the authenticated user.
     *
     * @param userId the authenticated user ID
     * @return success result
     */
    @PostMapping("/read-all")
    public Result<Map<String, Object>> markAllRead(@RequestHeader("X-User-Id") Long userId) {
        int updatedCount = notificationService.markAllRead(userId);
        return Result.success(Map.of("updatedCount", updatedCount));
    }
}
