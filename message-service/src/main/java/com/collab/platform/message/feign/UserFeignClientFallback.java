package com.collab.platform.message.feign;

import com.collab.platform.common.core.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fallback factory for UserFeignClient — returns safe defaults when user-service is unavailable.
 */
@Component
public class UserFeignClientFallback implements FallbackFactory<UserFeignClient> {

    private static final Logger log = LoggerFactory.getLogger(UserFeignClientFallback.class);

    @Override
    public UserFeignClient create(Throwable cause) {
        log.error("UserFeignClient fallback triggered", cause);
        return new UserFeignClient() {

            @Override
            public Result<UserBriefDTO> getUserInfo(Long userId) {
                UserBriefDTO dto = new UserBriefDTO();
                dto.setId(userId);
                dto.setUsername("unknown");
                dto.setNickname("未知用户");
                dto.setAvatar(null);
                return Result.success(dto);
            }

            @Override
            public Result<Map<Long, UserBriefDTO>> batchGetUserInfo(List<Long> userIds) {
                Map<Long, UserBriefDTO> map = new HashMap<>();
                for (Long userId : userIds) {
                    UserBriefDTO dto = new UserBriefDTO();
                    dto.setId(userId);
                    dto.setUsername("unknown");
                    dto.setNickname("未知用户");
                    dto.setAvatar(null);
                    map.put(userId, dto);
                }
                return Result.success(map);
            }
        };
    }
}
