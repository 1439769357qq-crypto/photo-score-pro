# 使用兼容性更好的 Ubuntu 版本，内置 glibc
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# 替换 Ubuntu 官方源为阿里云镜像（国内高速），然后安装 OpenCV 运行时依赖
RUN sed -i 's/archive.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list && \
    sed -i 's/security.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
        libopencv-imgcodecs4.5 \
        libopencv-videoio4.5 \
        libgomp1 \
    && rm -rf /var/lib/apt/lists/*

# 复制打包好的 Spring Boot JAR
COPY target/photo-score-pro-2.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]