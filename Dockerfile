# 第一阶段：使用 Maven 镜像来构建项目
FROM maven:3.9-amazoncorretto-17 AS build
COPY . .
RUN mvn clean package -DskipTests

# 第二阶段：使用更小的 JRE 镜像来运行项目
FROM openjdk:17-jdk-slim
COPY --from=build /target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]