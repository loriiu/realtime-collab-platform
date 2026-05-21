package com.collab.platform.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Session entity mapped to the {@code sessions} table.
 */
@Data
@TableName("sessions")
public class Session implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Snowflake ID. */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** Unique session key: sorted userIds concatenated with colon. */
    private String sessionKey;

    /** First participant user ID. */
    private Long userA;

    /** Second participant user ID. */
    private Long userB;

    /** Preview of the last message in this session. */
    private String lastMessage;

    /** Timestamp of the last message. */
    private LocalDateTime lastMessageTime;

    /** Creation timestamp. */
    private LocalDateTime createTime;
}
