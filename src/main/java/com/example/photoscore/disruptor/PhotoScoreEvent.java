package com.example.photoscore.disruptor;

import com.example.photoscore.pojo.PhotoScoreResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.CompletableFuture;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhotoScoreEvent {
    private MultipartFile file;
    private String fileHash;
    private String clientIp;
    private String userAgent;
    private CompletableFuture<PhotoScoreResponse> resultFuture;
}