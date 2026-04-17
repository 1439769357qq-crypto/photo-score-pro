package com.example.photoscore.pojo;

import com.example.photoscore.disruptor.PhotoScoreEvent;
import com.example.photoscore.service.impl.PhotoScoreServiceImpl;
import com.lmax.disruptor.EventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Disruptor事件处理器
 * 这个类实现了 EventHandler，直接被 Disruptor 调度，打破了循环依赖
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DisruptorEventProcessor implements EventHandler<PhotoScoreEvent> {

    private final PhotoScoreServiceImpl photoScoreService;

    @Override
    public void onEvent(PhotoScoreEvent event, long sequence, boolean endOfBatch) {
        log.debug("Disruptor 处理评分事件: fileName={}, sequence={}",
                event.getFile().getOriginalFilename(), sequence);

        PhotoScoreResponse response;
        try {
            // 调用同步评分方法
            CompositeScoreResult result = photoScoreService.performFullScoring(event.getFile());

            // 构建响应
            Map<String, BigDecimal> scoreDetails = new HashMap<>();
            if (result.getScoringResults() != null) {
                result.getScoringResults().forEach(r ->
                        scoreDetails.put(r.getScorerName(), r.getScore()));
            }

            response = PhotoScoreResponse.builder()
                    .fileName(event.getFile().getOriginalFilename())
                    .fileSize(event.getFile().getSize())
                    .dimension(result.getImageWidth() + "x" + result.getImageHeight())
                    .qualityScore(result.getTotalScore())
                    .isPass(result.getTotalScore().compareTo(new BigDecimal("60")) >= 0)
                    .scoreDetails(scoreDetails)
                    .scoreReasons(result.getComments())
                    .isDuplicate(false)
                    .build();

        } catch (Exception e) {
            log.error("Disruptor 评分处理失败: fileName={}",
                    event.getFile().getOriginalFilename(), e);
            response = PhotoScoreResponse.builder()
                    .fileName(event.getFile().getOriginalFilename())
                    .qualityScore(BigDecimal.ZERO)
                    .isPass(false)
                    .scoreReasons(java.util.Collections.singletonList("评分失败: " + e.getMessage()))
                    .isDuplicate(false)
                    .build();
        }

        // 将结果传递给异步 Future，调用方解除阻塞
        event.getResultFuture().complete(response);
    }
}