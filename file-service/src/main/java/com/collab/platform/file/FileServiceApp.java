package com.collab.platform.file;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * File Service entry point — file upload/download backed by MinIO.
 */
@SpringBootApplication(scanBasePackages = {
        "com.collab.platform.file",
        "com.collab.platform.common"
})
@EnableDiscoveryClient
@MapperScan("com.collab.platform.file.mapper")
public class FileServiceApp {

    public static void main(String[] args) {
        SpringApplication.run(FileServiceApp.class, args);
    }
}
