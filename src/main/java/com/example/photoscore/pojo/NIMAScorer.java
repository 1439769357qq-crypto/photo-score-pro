package com.example.photoscore.pojo;

import ai.djl.Application;
import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;

/**
 * NIMA 美学/技术质量评分器 —— Predictor 池化版本
 * 
 * 依赖：Apache Commons Pool2
 * Maven: org.apache.commons:commons-pool2:2.12.0
 * 
 * 优化点：
 * 1. ZooModel 保持单例共享（线程安全）
 * 2. Predictor 通过对象池复用，避免每次预测新建/销毁的开销
 * 3. 支持池化参数通过配置文件调整
 */
@Slf4j
@Component
public class NIMAScorer {

    private ZooModel<Image, float[]> aestheticModel;
    private ZooModel<Image, float[]> technicalModel;
    private ImageFactory imageFactory;

    private GenericObjectPool<Predictor<Image, float[]>> aestheticPredictorPool;
    private GenericObjectPool<Predictor<Image, float[]>> technicalPredictorPool;

    @Value("${photoscore.nima.enabled:true}")
    private boolean nimaEnabled;

    @Value("${photoscore.nima.model-path:src/main/resources/models/}")
    private String modelPath;

    @Value("${photoscore.nima.pool.max-total:8}")
    private int poolMaxTotal;

    @Value("${photoscore.nima.pool.max-idle:4}")
    private int poolMaxIdle;

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

            Criteria<Image, float[]> technicalCriteria = Criteria.builder()
                    .setTypes(Image.class, float[].class)
                    .optApplication(Application.CV.IMAGE_CLASSIFICATION)
                    .optModelUrls(modelPath + "nima_technical.onnx")
                    .optEngine("OnnxRuntime")
                    .build();
            technicalModel = ModelZoo.loadModel(technicalCriteria);

            // 配置对象池
            GenericObjectPoolConfig<Predictor<Image, float[]>> config = new GenericObjectPoolConfig<>();
            config.setMaxTotal(poolMaxTotal);          // 池最大对象数
            config.setMaxIdle(poolMaxIdle);            // 最大空闲数
            config.setMinIdle(2);                       // 最小空闲数（预热）
            config.setBlockWhenExhausted(true);         // 池耗尽时阻塞等待
            config.setMaxWaitMillis(5000);              // 最大等待 5 秒
            config.setTestOnBorrow(true);               // 借出时校验
            config.setTestOnReturn(true);               // 归还时校验
            config.setTimeBetweenEvictionRunsMillis(60000); //  eviction 线程周期

            aestheticPredictorPool = new GenericObjectPool<>(
                    new PredictorFactory(aestheticModel), config);
            technicalPredictorPool = new GenericObjectPool<>(
                    new PredictorFactory(technicalModel), config);

            // 预热池，提前创建最小空闲对象，避免首次请求冷启动
            aestheticPredictorPool.preparePool();
            technicalPredictorPool.preparePool();

            log.info("NIMA评分器初始化成功，Predictor池化配置: maxTotal={}, maxIdle={}", 
                    poolMaxTotal, poolMaxIdle);
        } catch (Exception e) {
            log.warn("NIMA模型加载失败，将使用降级评分模式: {}", e.getMessage());
            nimaEnabled = false;
        }
    }

    public double scoreAesthetic(BufferedImage bufferedImage) {
        if (!nimaEnabled || aestheticPredictorPool == null) return 0.65;
        Predictor<Image, float[]> predictor = null;
        try {
            predictor = aestheticPredictorPool.borrowObject();
            Image img = imageFactory.fromImage(bufferedImage);
            float[] predictions = predictor.predict(img);
            return computeMeanScore(predictions);
        } catch (Exception e) {
            log.error("NIMA美学评分失败: {}", e.getMessage());
            return 0.65;
        } finally {
            if (predictor != null) {
                try { 
                    aestheticPredictorPool.returnObject(predictor); 
                } catch (Exception ex) { 
                    log.warn("归还Aesthetic Predictor失败", ex); 
                }
            }
        }
    }

    public double scoreTechnical(BufferedImage bufferedImage) {
        if (!nimaEnabled || technicalPredictorPool == null) return 0.60;
        Predictor<Image, float[]> predictor = null;
        try {
            predictor = technicalPredictorPool.borrowObject();
            Image img = imageFactory.fromImage(bufferedImage);
            float[] predictions = predictor.predict(img);
            return computeMeanScore(predictions);
        } catch (Exception e) {
            log.error("NIMA技术评分失败: {}", e.getMessage());
            return 0.60;
        } finally {
            if (predictor != null) {
                try { 
                    technicalPredictorPool.returnObject(predictor); 
                } catch (Exception ex) { 
                    log.warn("归还Technical Predictor失败", ex); 
                }
            }
        }
    }

    private double computeMeanScore(float[] predictions) {
        if (predictions == null || predictions.length == 0) return 0.5;
        double sum = 0;
        double weightSum = 0;
        for (int i = 0; i < predictions.length; i++) {
            int score = i + 1;
            sum += score * predictions[i];
            weightSum += predictions[i];
        }
        if (weightSum == 0) return 0.5;
        double meanScore = sum / weightSum;
        return (meanScore - 1.0) / 9.0;
    }

    public boolean isAvailable() {
        return nimaEnabled && aestheticModel != null && technicalModel != null;
    }

    @PreDestroy
    public void destroy() {
        if (aestheticPredictorPool != null) {
            aestheticPredictorPool.close();
        }
        if (technicalPredictorPool != null) {
            technicalPredictorPool.close();
        }
        if (aestheticModel != null) aestheticModel.close();
        if (technicalModel != null) technicalModel.close();
    }

    /**
     * Predictor 对象池工厂
     */
    private static class PredictorFactory extends BasePooledObjectFactory<Predictor<Image, float[]>> {
        private final ZooModel<Image, float[]> model;

        PredictorFactory(ZooModel<Image, float[]> model) {
            this.model = model;
        }

        @Override
        public Predictor<Image, float[]> create() throws Exception {
            return model.newPredictor();
        }

        @Override
        public PooledObject<Predictor<Image, float[]>> wrap(Predictor<Image, float[]> predictor) {
            return new DefaultPooledObject<>(predictor);
        }

        @Override
        public boolean validateObject(PooledObject<Predictor<Image, float[]>> p) {
            // 简单校验：对象非空即可；如需深度校验可尝试一次空推理
            return p.getObject() != null;
        }

        @Override
        public void destroyObject(PooledObject<Predictor<Image, float[]>> p) throws Exception {
            Predictor<Image, float[]> predictor = p.getObject();
            if (predictor != null) {
                predictor.close();
            }
        }
    }
}
