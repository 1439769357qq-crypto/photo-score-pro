package com.example.photoscore.pojo;

import com.example.photoscore.disruptor.PhotoScoreEvent;
import com.example.photoscore.service.impl.PhotoScoreServiceImpl;
import com.lmax.disruptor.EventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Disruptor事件处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DisruptorEventProcessor implements EventHandler<PhotoScoreEvent> {

    private final PhotoScoreServiceImpl photoScoreService;

    @Override
    public void onEvent(PhotoScoreEvent event, long sequence, boolean endOfBatch) {
        String fileName = "unknown";
        try {
            if (event == null || event.getFile() == null) {
                log.warn("Disruptor 收到空事件或空文件, sequence={}", sequence);
                return;
            }
            fileName = event.getFile().getOriginalFilename();
            log.debug("Disruptor 处理评分事件: fileName={}, sequence={}", fileName, sequence);

            CompositeScoreResult result = photoScoreService.performFullScoring(
                    event.getFile(),
                    event.getClientIp(),
                    event.getUserAgent()
            );

            Map<String, BigDecimal> scoreDetails = new HashMap<>();
            if (result.getScoringResults() != null) {
                result.getScoringResults().forEach(r ->
                        scoreDetails.put(r.getScorerName(), r.getScore()));
            }

            PhotoScoreResponse response = PhotoScoreResponse.builder()
                    .fileName(fileName)
                    .fileSize(event.getFile().getSize())
                    .dimension(result.getImageWidth() + "x" + result.getImageHeight())
                    .qualityScore(result.getTotalScore())
                    .isPass(result.getTotalScore().compareTo(new BigDecimal("60")) >= 0)
                    .scoreDetails(scoreDetails)
                    .scoreReasons(result.getComments())
                    .improvementSuggestions(result.getSuggestions())
                    .isDuplicate(false)
                    .build();

            event.getResultFuture().complete(response);

        } catch (Exception e) {
            log.error("Disruptor 评分处理失败: fileName={}", fileName, e);
            PhotoScoreResponse errorResponse = PhotoScoreResponse.builder()
                    .fileName(fileName)
                    .qualityScore(BigDecimal.ZERO)
                    .isPass(false)
                    .scoreReasons(Collections.singletonList("评分失败: " + e.getMessage()))
                    .improvementSuggestions(Collections.emptyList())
                    .isDuplicate(false)
                    .build();
            event.getResultFuture().complete(errorResponse);
        }
    }
}
