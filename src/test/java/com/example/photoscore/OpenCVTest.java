package com.example.photoscore;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

public class OpenCVTest {
    public static void main(String[] args) {
        // 加载OpenCV本地库
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        
        // 创建一个3x3的单位矩阵并打印，验证基本功能
        Mat mat = Mat.eye(3, 3, CvType.CV_8UC1);
        System.out.println("OpenCV 配置成功！");
        System.out.println("mat = " + mat.dump());
    }
}