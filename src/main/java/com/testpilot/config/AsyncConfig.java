package com.testpilot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步执行配置：任务线程池 + 浏览器并发闸门。
 * <p>核心策略：大线程池承载并发任务，小信号量限制「同时打开的浏览器数」，超出的任务排队而非 OOM。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor(
            @Value("${testpilot.async.core-pool-size:10}") int corePoolSize,
            @Value("${testpilot.async.max-pool-size:30}") int maxPoolSize,
            @Value("${testpilot.async.queue-capacity:100}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("testpilot-task-");
        // 队列满时由调用线程执行，避免静默丢弃
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 浏览器并发闸门：限制同时打开的浏览器数量（每任务一个独立浏览器会话）。
     */
    @Bean
    public Semaphore browserSemaphore(
            @Value("${testpilot.async.max-browser-concurrency:6}") int permits) {
        return new Semaphore(permits);
    }
}
