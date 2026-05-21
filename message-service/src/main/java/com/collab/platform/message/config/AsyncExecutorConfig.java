package com.collab.platform.message.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Async executor configuration for non-blocking message persistence.
 * Uses CallerRunsPolicy to avoid message loss under overload.
 */
@Configuration
public class AsyncExecutorConfig {

    /**
     * Dedicated executor for async message persistence.
     * Core: 4, Max: 8, Queue: 1000, Rejection: CallerRunsPolicy.
     *
     * @return thread pool executor service
     */
    @Bean("messagePersistenceExecutor")
    public ExecutorService messagePersistenceExecutor() {
        return new ThreadPoolExecutor(
                4,
                8,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1000),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
