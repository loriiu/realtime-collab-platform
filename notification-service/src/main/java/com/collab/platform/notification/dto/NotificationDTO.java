package com.collab.platform.notification.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Notification data transfer object for API responses.
 */
@Data
public class NotificationDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String type;
    private String title;
    private String content;
    private String sourceId;
    private String sourceType;
    private Integer isRead;
    private LocalDateTime createTime;
}
