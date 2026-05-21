package com.collab.platform.notification;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Notification Service entry point — consumes domain events from RabbitMQ
 * and persists user notifications.
 */
@SpringBootApplication(scanBasePackages = {
        "com.collab.platform.notification",
        "com.collab.platform.common"
})
@EnableDiscoveryClient
@MapperScan("com.collab.platform.notification.mapper")
public class NotificationServiceApp {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApp.class, args);
    }
}
