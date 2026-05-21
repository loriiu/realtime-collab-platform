package com.collab.platform.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Notification entity mapped to the {@code notifications} table.
 */
@Data
@TableName("notifications")
public class Notification implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Auto-increment primary key. */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** Target user ID. */
    private Long userId;

    /** Notification type: MESSAGE, FILE_UPLOADED, SYSTEM. */
    private String type;

    /** Notification title (brief summary). */
    private String title;

    /** Notification content / body. */
    private String content;

    /** Source entity ID (e.g. message ID, file ID). */
    private String sourceId;

    /** Source entity type (e.g. "message", "file"). */
    private String sourceType;

    /** Read flag: 0=unread, 1=read. */
    private Integer isRead;

    /** Creation timestamp. */
    private LocalDateTime createTime;
}
