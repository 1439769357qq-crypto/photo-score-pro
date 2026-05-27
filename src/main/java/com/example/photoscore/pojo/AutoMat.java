package com.example.photoscore.pojo;

import org.opencv.core.Mat;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenCV Mat 的 AutoCloseable 包装器
 * 配合 try-with-resources 实现自动释放，彻底杜绝内存泄漏和 NPE
 * 
 * 用法示例：
 * try (AutoMat mat = AutoMat.of(OpenCVUtil.bufferedImageToMat(image));
 *      AutoMat gray = AutoMat.empty()) {
 *     Imgproc.cvtColor(mat.get(), gray.get(), Imgproc.COLOR_BGR2GRAY);
 *     // ... 业务逻辑 ...
 * } // 此处自动释放 mat 和 gray
 */
public class AutoMat implements AutoCloseable {

    private Mat mat;
    private final List<AutoMat> children = new ArrayList<>();
    private boolean closed = false;

    private AutoMat(Mat mat) {
        this.mat = mat;
    }

    /** 包装已有 Mat */
    public static AutoMat of(Mat mat) {
        return new AutoMat(mat);
    }

    /** 创建一个空的 Mat（用于接收 OpenCV 操作输出） */
    public static AutoMat empty() {
        return new AutoMat(new Mat());
    }

    /** 获取底层 Mat，用于传入 OpenCV API */
    public Mat get() {
        if (closed) {
            throw new IllegalStateException("AutoMat has been closed");
        }
        return mat;
    }

    /** 
     * 创建子 AutoMat（用于临时中间变量）
     * 子对象会随父对象一起关闭
     */
    public AutoMat child(Mat childMat) {
        AutoMat child = new AutoMat(childMat);
        children.add(child);
        return child;
    }

    public AutoMat childEmpty() {
        return child(new Mat());
    }

    @Override
    public void close() {
        if (closed) return;
        // 先释放所有子对象
        for (AutoMat child : children) {
            child.close();
        }
        children.clear();
        // 再释放自己
        if (mat != null) {
            mat.release();
            mat = null;
        }
        closed = true;
    }

    /** 便捷方法：安全释放多个 Mat（兼容旧代码） */
    public static void safeRelease(Mat... mats) {
        if (mats == null) return;
        for (Mat m : mats) {
            if (m != null) m.release();
        }
    }
}
