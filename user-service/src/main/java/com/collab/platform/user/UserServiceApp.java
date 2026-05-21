package com.collab.platform.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * User Service entry point.
 */
@SpringBootApplication(scanBasePackages = {
        "com.collab.platform.user",
        "com.collab.platform.common"
})
@EnableDiscoveryClient
@MapperScan("com.collab.platform.user.mapper")
public class UserServiceApp {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApp.class, args);
    }
}
