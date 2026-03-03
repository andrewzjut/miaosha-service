package com.example.demo.infrastructure;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka 配置类
 */
@Configuration
public class KafkaConfig {

    public static final String SECKILL_TOPIC = "seckill-orders";

    /**
     * 创建秒杀订单主题
     */
    @Bean
    public NewTopic seckillTopic() {
        return TopicBuilder.name(SECKILL_TOPIC)
                .partitions(10) // 10 个分区，支持并行消费
                .replicas(1)    // 单副本（生产环境应设置为 3）
                .build();
    }
}
