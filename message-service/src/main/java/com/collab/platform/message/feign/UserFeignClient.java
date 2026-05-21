package com.collab.platform.message.feign;

import com.collab.platform.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * Feign client for user-service, with fallback factory for resilience.
 */
@FeignClient(name = "user-service", fallbackFactory = UserFeignClientFallback.class)
public interface UserFeignClient {

    /**
     * Get a single user's brief info.
     *
     * @param userId the user ID (from X-User-Id header)
     * @return user brief info
     */
    @GetMapping("/user/info")
    Result<UserBriefDTO> getUserInfo(@RequestHeader("X-User-Id") Long userId);

    /**
     * Batch query user brief info by IDs.
     *
     * @param userIds list of user IDs
     * @return map of userId → UserBriefDTO
     */
    @GetMapping("/user/batch")
    Result<Map<Long, UserBriefDTO>> batchGetUserInfo(@RequestParam("userIds") List<Long> userIds);
}
