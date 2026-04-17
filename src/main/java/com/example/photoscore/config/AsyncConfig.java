package com.example.photoscore.config;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步并行处理配置
 * 用于批量照片评分时并发处理，充分利用多核CPU
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：设置为CPU核心数，充分利用硬件
        executor.setCorePoolSize(Runtime.getRuntime().availableProcessors());
        // 最大线程数：核心数的2倍，应对突发流量
        executor.setMaxPoolSize(Runtime.getRuntime().availableProcessors() * 2);
        // 队列容量：缓冲等待执行的任务
        executor.setQueueCapacity(500);
        // 线程名前缀，方便日志排查
        executor.setThreadNamePrefix("photo-score-async-");
        // 当线程池关闭时，等待所有任务完成
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 等待时间：60秒
        executor.setAwaitTerminationSeconds(60);
        // 拒绝策略：由调用线程执行（保证任务不丢失）
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        // 异步任务未捕获异常的处理：打印日志
        return (throwable, method, objects) -> {
            System.err.println("异步任务执行异常: " + method.getName());
            throwable.printStackTrace();
        };
    }
}