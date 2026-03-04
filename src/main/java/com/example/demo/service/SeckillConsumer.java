package com.example.demo.service;

import com.example.demo.dto.OrderResult;
import com.example.demo.dto.SeckillMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import static com.example.demo.infrastructure.KafkaConfig.SECKILL_TOPIC;

/**
 * 秒杀消费者服务
 * 监听 Kafka 秒杀消息，异步处理订单
 */
@Service
public class SeckillConsumer {

    private static final Logger log = LoggerFactory.getLogger(SeckillConsumer.class);

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public SeckillConsumer(OrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    /**
     * 监听秒杀订单消息
     * 自动提交 offset
     */
    @KafkaListener(topics = SECKILL_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String message) {
        log.info("收到秒杀消息：{}", message);

        try {
            // 解析消息
            SeckillMessage seckillMessage = parseMessage(message);
            if (seckillMessage == null) {
                log.error("解析消息失败：{}", message);
                return;
            }

            // 处理订单
            Long userId = seckillMessage.userId();
            Long seckillId = seckillMessage.seckillId();

            OrderResult result = orderService.createOrder(userId, seckillId);

            if (result.success()) {
                log.info("订单创建成功：userId={}, orderId={}", userId, result.orderId());
            } else {
                log.warn("订单创建失败：userId={}, reason={}", userId, result.message());
            }

        } catch (Exception e) {
            log.error("处理秒杀消息失败：message={}", message, e);
            // 记录错误，继续处理下一条消息
        }
    }

    /**
     * 解析消息
     */
    private SeckillMessage parseMessage(String message) {
        try {
            return objectMapper.readValue(message, SeckillMessage.class);
        } catch (JsonProcessingException e) {
            log.error("JSON 解析失败：{}", message, e);
            return null;
        }
    }
}
