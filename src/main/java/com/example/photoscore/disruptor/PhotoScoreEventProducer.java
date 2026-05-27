package com.example.photoscore.disruptor;

import com.example.photoscore.pojo.PhotoScoreResponse;
import com.lmax.disruptor.RingBuffer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.CompletableFuture;

@Component
public class PhotoScoreEventProducer {

    private final ObjectProvider<RingBuffer<PhotoScoreEvent>> ringBufferProvider;

    // 通过 ObjectProvider 延迟获取 RingBuffer，避免代理 final 类的问题
    public PhotoScoreEventProducer(ObjectProvider<RingBuffer<PhotoScoreEvent>> ringBufferProvider) {
        this.ringBufferProvider = ringBufferProvider;
    }

    public CompletableFuture<PhotoScoreResponse> publishEvent(MultipartFile file,
                                                              String fileHash,
                                                              String clientIp,
                                                              String userAgent) {
        RingBuffer<PhotoScoreEvent> ringBuffer = ringBufferProvider.getIfAvailable();
        if (ringBuffer == null) {
            throw new IllegalStateException("RingBuffer 尚未初始化");
        }

        CompletableFuture<PhotoScoreResponse> future = new CompletableFuture<>();
        long sequence = ringBuffer.next();
        try {
            PhotoScoreEvent event = ringBuffer.get(sequence);
            event.setFile(file);
            event.setFileHash(fileHash);
            event.setClientIp(clientIp);
            event.setUserAgent(userAgent);
            event.setResultFuture(future);
        } finally {
            ringBuffer.publish(sequence);
        }
        return future;
    }
}