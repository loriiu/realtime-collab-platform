package com.collab.platform.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API Gateway entry point (Spring Cloud Gateway + WebFlux).
 */
@SpringBootApplication(scanBasePackages = {"com.collab.platform.gateway", "com.collab.platform.common.security"})
@EnableDiscoveryClient
public class GatewayApp {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApp.class, args);
    }
}
