package com.collab.platform.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Message entity mapped to the {@code messages} table.
 */
@Data
@TableName("messages")
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Snowflake ID (assigned by application, not auto-increment). */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** Session ID that this message belongs to. */
    private Long sessionId;

    /** Sender user ID. */
    private Long senderId;

    /** Receiver user ID. */
    private Long receiverId;

    /** Message content (text, or serialized rich content). */
    private String content;

    /** Message type: TEXT, IMAGE, SYSTEM. */
    private String type;

    /** Message protocol version. */
    private Integer version;

    /** Delivery status: 0=sending, 1=delivered, 2=read. */
    private Integer status;

    /** Logical delete flag: 0=normal, 1=deleted. */
    private Integer isDeleted;

    /** Creation timestamp. */
    private LocalDateTime createTime;
}
