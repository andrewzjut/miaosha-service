package com.example.demo.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 基础设施配置类
 */
@Configuration
@EnableAsync
@EnableScheduling
public class InfrastructureConfig {
    // Redis、Redisson 配置在 RedisConfig 中
    // Kafka 配置在 KafkaConfig 中
}
