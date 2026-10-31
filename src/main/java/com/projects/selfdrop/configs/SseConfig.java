package com.projects.selfdrop.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

@Configuration
public class SseConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService sseExecutor() {
        return Executors.newFixedThreadPool(20, threadFactory("sse-worker"));   // bounded, not cached
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService sseBroadcast() {
        // One thread serializes broadcasts, so events arrive in publication order
        // and an upload never blocks on a slow SSE consumer.
        return Executors.newSingleThreadExecutor(threadFactory("sse-broadcast"));
    }

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService sseHeartbeat() {
        return Executors.newScheduledThreadPool(2, threadFactory("sse-heartbeat"));
    }

    private static ThreadFactory threadFactory(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }
}
