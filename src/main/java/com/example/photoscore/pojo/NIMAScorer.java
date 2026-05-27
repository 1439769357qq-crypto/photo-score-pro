package com.example.photoscore.pojo;

import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.util.NDImageUtils;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.Batchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

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
 * 
 * 修改说明：
 * 由于 nima_technical.onnx 难以获取，技术质量模型复用了美学模型，
 * 两个评分器使用同一个 ONNX 模型实例。
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
            Path modelFile = resolveModelFile("nima_aesthetic.onnx");

            log.info("NIMA模型路径: {}", modelFile);
            log.info("NIMA模型文件是否存在: {}", Files.exists(modelFile));

            if (!Files.exists(modelFile)) {
                throw new FileNotFoundException("NIMA模型文件不存在: " + modelFile);
            }

            Criteria<Image, float[]> aestheticCriteria = Criteria.builder()
                    .setTypes(Image.class, float[].class)
                    .optModelPath(modelFile)
                    .optEngine("OnnxRuntime")
                    .optTranslator(new NimaOnnxTranslator())
                    .build();

            aestheticModel = ModelZoo.loadModel(aestheticCriteria);

            technicalModel = aestheticModel;

            GenericObjectPoolConfig<Predictor<Image, float[]>> config = new GenericObjectPoolConfig<>();
            config.setMaxTotal(poolMaxTotal);
            config.setMaxIdle(poolMaxIdle);
            config.setMinIdle(2);
            config.setBlockWhenExhausted(true);
            config.setMaxWaitMillis(5000);
            config.setTestOnBorrow(true);
            config.setTestOnReturn(true);
            config.setTimeBetweenEvictionRunsMillis(60000);

            aestheticPredictorPool = new GenericObjectPool<>(
                    new PredictorFactory(aestheticModel), config);
            technicalPredictorPool = new GenericObjectPool<>(
                    new PredictorFactory(technicalModel), config);

            aestheticPredictorPool.preparePool();
            technicalPredictorPool.preparePool();

            log.info("NIMA评分器初始化成功（技术质量模型已复用美学模型），Predictor池化配置: maxTotal={}, maxIdle={}",
                    poolMaxTotal, poolMaxIdle);

        } catch (Exception e) {
            log.warn("NIMA模型加载失败，将使用降级评分模式: {}", e.getMessage(), e);
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

    private static class NimaOnnxTranslator implements Translator<Image, float[]> {

        @Override
        public NDList processInput(TranslatorContext ctx, Image input) {
            NDArray array = input.toNDArray(ctx.getNDManager(), Image.Flag.COLOR);

            // 模型要求输入尺寸为 224x224
            array = NDImageUtils.resize(array, 224, 224);

            /*
             * 关键修复：
             * 不要使用 NDImageUtils.toTensor(array)
             *
             * toTensor 会把图片从 HWC 转成 CHW：
             * [224, 224, 3] -> [3, 224, 224]
             *
             * 但你的 NIMA ONNX 模型期望的是 NHWC：
             * [1, 224, 224, 3]
             */
            array = array.toType(ai.djl.ndarray.types.DataType.FLOAT32, false).div(255.0f);

            // ImageNet 均值方差归一化，保持 HWC 格式
            NDArray mean = ctx.getNDManager().create(
                    new float[]{0.485f, 0.456f, 0.406f},
                    new ai.djl.ndarray.types.Shape(1, 1, 3)
            );

            NDArray std = ctx.getNDManager().create(
                    new float[]{0.229f, 0.224f, 0.225f},
                    new ai.djl.ndarray.types.Shape(1, 1, 3)
            );

            array = array.sub(mean).div(std);

            // HWC -> NHWC: [224, 224, 3] -> [1, 224, 224, 3]
            array = array.expandDims(0);

            return new NDList(array);
        }

        @Override
        public float[] processOutput(TranslatorContext ctx, NDList list) {
            NDArray output = list.singletonOrThrow();

            // 常见输出是 [1,10]，压成 [10]
            output = output.squeeze();

            float[] raw = output.toFloatArray();

            // 如果模型输出不是概率分布，就做一次 softmax
            double sum = 0;
            boolean hasNegative = false;

            for (float v : raw) {
                sum += v;
                if (v < 0) {
                    hasNegative = true;
                }
            }

            if (hasNegative || sum < 0.95 || sum > 1.05) {
                output = output.softmax(0);
                raw = output.toFloatArray();
            }

            return raw;
        }

        @Override
        public Batchifier getBatchifier() {
            return null;
        }
    }

    private Path resolveModelFile(String fileName) throws IOException {
        // 1. 优先按 yml 配置的物理路径查找
        Path fileSystemPath = Paths.get(modelPath, fileName)
                .toAbsolutePath()
                .normalize();

        log.info("NIMA模型物理路径: {}", fileSystemPath);
        log.info("NIMA模型物理文件是否存在: {}", Files.exists(fileSystemPath));

        if (Files.exists(fileSystemPath)) {
            return fileSystemPath;
        }

        // 2. Render / jar 环境下，从 classpath 读取 resources/models/nima_aesthetic.onnx
        ClassPathResource resource = new ClassPathResource("models/" + fileName);

        log.info("NIMA模型classpath路径: classpath:/models/{}", fileName);
        log.info("NIMA模型classpath资源是否存在: {}", resource.exists());

        if (!resource.exists()) {
            throw new FileNotFoundException(
                    "NIMA模型文件不存在。已尝试物理路径: "
                            + fileSystemPath
                            + "，以及 classpath:/models/"
                            + fileName
            );
        }

        // 3. DJL 的 optModelPath 需要真实文件路径，所以复制到临时目录
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "photoscore-models");
        Files.createDirectories(tempDir);

        Path tempModelFile = tempDir.resolve(fileName);

        try (InputStream inputStream = resource.getInputStream()) {
            Files.copy(inputStream, tempModelFile, StandardCopyOption.REPLACE_EXISTING);
        }

        log.info("NIMA模型已从classpath复制到临时路径: {}", tempModelFile);

        return tempModelFile;
    }
}