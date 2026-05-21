package com.collab.platform.message.config;

import cn.hutool.core.util.IdUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Snowflake ID generator configuration.
 * Each message-service instance must have a unique worker-id.
 */
@Configuration
public class SnowflakeConfig {

    @Value("${snowflake.worker-id:1}")
    private long workerId;

    @Value("${snowflake.center-id:1}")
    private long centerId;

    /**
     * Create a Snowflake instance based on configured worker-id and center-id.
     *
     * @return Snowflake ID generator
     */
    @Bean
    public cn.hutool.core.lang.Snowflake snowflake() {
        return IdUtil.createSnowflake(workerId, centerId);
    }
}
