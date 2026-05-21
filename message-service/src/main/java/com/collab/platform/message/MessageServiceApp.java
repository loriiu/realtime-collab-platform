package com.collab.platform.message;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Message Service entry point — distributed WebSocket realtime communication.
 */
@SpringBootApplication(scanBasePackages = {
        "com.collab.platform.message",
        "com.collab.platform.common"
})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.collab.platform.message.feign")
@MapperScan("com.collab.platform.message.mapper")
public class MessageServiceApp {

    public static void main(String[] args) {
        SpringApplication.run(MessageServiceApp.class, args);
    }
}
