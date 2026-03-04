# Dockerfile - Apple Silicon 适配
# 运行阶段 - 使用 Eclipse Temurin JDK17 JRE (ARM64)
FROM --platform=linux/arm64 eclipse-temurin:17-jre

WORKDIR /app

# 复制已构建的 jar 包
COPY target/*.jar app.jar

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
