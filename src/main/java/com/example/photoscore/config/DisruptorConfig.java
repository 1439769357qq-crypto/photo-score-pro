package com.example.photoscore.config;


import com.example.photoscore.disruptor.PhotoScoreEvent;
import com.example.photoscore.pojo.DisruptorEventProcessor;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Configuration
public class DisruptorConfig {

    @Value("${photoscore.disruptor.ring-buffer-size:4096}")
    private int ringBufferSize;

    @Value("${photoscore.disruptor.worker-threads:4}")
    private int workerThreads;

    private Disruptor<PhotoScoreEvent> disruptor;

    @Bean
    public EventFactory<PhotoScoreEvent> eventFactory() {
        return PhotoScoreEvent::new;
    }

    @Bean
    public RingBuffer<PhotoScoreEvent> ringBuffer(ObjectProvider<DisruptorEventProcessor> processorProvider) {
        log.info("初始化 Disruptor: ringBufferSize={}, workerThreads={}", ringBufferSize, workerThreads);

        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "disruptor-worker-" + counter.getAndIncrement());
                thread.setDaemon(false);
                return thread;
            }
        };

        disruptor = new Disruptor<>(
                eventFactory(),
                ringBufferSize,
                threadFactory,
                ProducerType.MULTI,
                new BlockingWaitStrategy()
        );

        // 通过 ObjectProvider 延迟获取处理器
        DisruptorEventProcessor processor = processorProvider.getIfAvailable();
        if (processor != null) {
            disruptor.handleEventsWith(processor);
        } else {
            log.error("DisruptorEventProcessor 不可用！");
        }
        disruptor.start();

        log.info("Disruptor 启动成功");
        return disruptor.getRingBuffer();
    }

    @PreDestroy
    public void shutdown() {
        if (disruptor != null) {
            disruptor.shutdown();
            log.info("Disruptor 已关闭");
        }
    }
}