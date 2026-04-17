package com.example.photoscore.pojo;

import ai.djl.Application;
import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.IOException;

@Slf4j
@Component
public class NIMAScorer {

    private ZooModel<Image, float[]> aestheticModel;
    private ZooModel<Image, float[]> technicalModel;
    private Predictor<Image, float[]> aestheticPredictor;
    private Predictor<Image, float[]> technicalPredictor;

    @Value("${photoscore.nima.enabled:true}")
    private boolean nimaEnabled;

    @Value("${photoscore.nima.model-path:src/main/resources/models/}")
    private String modelPath;

    private ImageFactory imageFactory;

    @PostConstruct
    public void init() {
        this.imageFactory = ImageFactory.getInstance();
        if (!nimaEnabled) {
            log.info("NIMA评分器已禁用");
            return;
        }

        try {
            Criteria<Image, float[]> aestheticCriteria = Criteria.builder()
                    .setTypes(Image.class, float[].class)
                    .optApplication(Application.CV.IMAGE_CLASSIFICATION)
                    .optModelUrls(modelPath + "nima_aesthetic.onnx")
                    .optEngine("OnnxRuntime")
                    .build();

            aestheticModel = ModelZoo.loadModel(aestheticCriteria);
            aestheticPredictor = aestheticModel.newPredictor();

            Criteria<Image, float[]> technicalCriteria = Criteria.builder()
                    .setTypes(Image.class, float[].class)
                    .optApplication(Application.CV.IMAGE_CLASSIFICATION)
                    .optModelUrls(modelPath + "nima_technical.onnx")
                    .optEngine("OnnxRuntime")
                    .build();

            technicalModel = ModelZoo.loadModel(technicalCriteria);
            technicalPredictor = technicalModel.newPredictor();

            log.info("NIMA评分器初始化成功");
        } catch (ModelNotFoundException | MalformedModelException | IOException e) {
            log.warn("NIMA模型加载失败，将使用降级评分模式: {}", e.getMessage());
            nimaEnabled = false;
        }
    }

    /**
     * 计算美学评分
     */
    public double scoreAesthetic(BufferedImage bufferedImage) {
        if (!nimaEnabled || aestheticPredictor == null) {
            return 0.65;
        }

        try {
            // 修正：使用 ImageFactory 实例的 fromImage 方法
            Image img = imageFactory.fromImage(bufferedImage);
            float[] predictions = aestheticPredictor.predict(img);
            return computeMeanScore(predictions);
        } catch (TranslateException e) {
            log.error("NIMA美学评分失败: {}", e.getMessage());
            return 0.65;
        }
    }

    /**
     * 计算技术质量评分
     */
    public double scoreTechnical(BufferedImage bufferedImage) {
        if (!nimaEnabled || technicalPredictor == null) {
            return 0.60;
        }

        try {
            Image img = imageFactory.fromImage(bufferedImage);
            float[] predictions = technicalPredictor.predict(img);
            return computeMeanScore(predictions);
        } catch (TranslateException e) {
            log.error("NIMA技术评分失败: {}", e.getMessage());
            return 0.60;
        }
    }

    private double computeMeanScore(float[] predictions) {
        if (predictions == null || predictions.length == 0) {
            return 0.5;
        }

        double sum = 0;
        double weightSum = 0;
        for (int i = 0; i < predictions.length; i++) {
            int score = i + 1;
            sum += score * predictions[i];
            weightSum += predictions[i];
        }

        if (weightSum == 0) {
            return 0.5;
        }

        double meanScore = sum / weightSum;
        return (meanScore - 1.0) / 9.0;
    }

    public boolean isAvailable() {
        return nimaEnabled && aestheticPredictor != null && technicalPredictor != null;
    }

    @PreDestroy
    public void destroy() {
        if (aestheticPredictor != null) aestheticPredictor.close();
        if (technicalPredictor != null) technicalPredictor.close();
        if (aestheticModel != null) aestheticModel.close();
        if (technicalModel != null) technicalModel.close();
    }
}