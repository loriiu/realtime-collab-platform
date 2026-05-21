package com.collab.platform.message.feign;

import lombok.Data;

/**
 * Lightweight user DTO for Feign deserialization from user-service.
 * Uses Long for id to ensure compatibility with user-service's User entity.
 */
@Data
public class UserBriefDTO {

    private Long id;

    private String username;

    private String nickname;

    private String avatar;
}
