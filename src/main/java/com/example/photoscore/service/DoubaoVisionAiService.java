package com.example.photoscore.service;

import com.example.photoscore.pojo.DoubaoVisionReviewRequest;
import com.example.photoscore.pojo.DoubaoVisionReviewResult;

import java.awt.image.BufferedImage;

public interface DoubaoVisionAiService {

    /**
     * 调用豆包视觉模型，生成视觉参考分、评分校验、专业点评和修图建议。
     *
     * @param request 本地评分系统结构化数据
     * @param image   要发送给豆包视觉模型的图片，建议传 processedImage
     * @return 豆包视觉评审结果；失败时返回 success=false，不抛异常影响主评分流程
     */
    DoubaoVisionReviewResult generateVisionReview(DoubaoVisionReviewRequest request,
                                                  BufferedImage image);
}
