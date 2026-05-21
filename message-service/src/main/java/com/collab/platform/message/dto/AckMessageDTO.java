package com.collab.platform.message.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * ACK (acknowledgement) DTO for message delivery/read status updates.
 */
@Data
public class AckMessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The message ID being acknowledged. */
    private Long messageId;

    /** New status: 1 = delivered, 2 = read. */
    private Integer status;
}
