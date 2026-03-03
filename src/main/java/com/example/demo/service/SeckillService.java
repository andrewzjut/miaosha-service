package com.example.demo.service;

import com.example.demo.domain.Seckill;
import com.example.demo.domain.SeckillResult;
import com.example.demo.dto.OrderResult;
import com.example.demo.dto.SeckillMessage;
import com.example.demo.dto.SeckillRequest;
import com.example.demo.infrastructure.RedisResultService;
import com.example.demo.infrastructure.RedisStockService;
import com.example.demo.repository.SeckillRepository;
import com.example.demo.repository.SeckillResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.example.demo.infrastructure.KafkaConfig.SECKILL_TOPIC;

/**
 * 秒杀核心服务
 * 负责处理秒杀请求的核心逻辑
 */
@Service
public class SeckillService {

    private static final Logger log = LoggerFactory.getLogger(SeckillService.class);

    private final SeckillRepository seckillRepository;
    private final RedisStockService redisStockService;
    private final RedisResultService redisResultService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final SeckillResultRepository seckillResultRepository;

    public SeckillService(SeckillRepository seckillRepository,
                          RedisStockService redisStockService,
                          RedisResultService redisResultService,
                          KafkaTemplate<String, String> kafkaTemplate,
                          SeckillResultRepository seckillResultRepository) {
        this.seckillRepository = seckillRepository;
        this.redisStockService = redisStockService;
        this.redisResultService = redisResultService;
        this.kafkaTemplate = kafkaTemplate;
        this.seckillResultRepository = seckillResultRepository;
    }

    /**
     * 处理秒杀请求
     * 流程：
     * 1. 验证秒杀活动是否有效
     * 2. 检查用户是否重复参与
     * 3. Redis 预扣减库存
     * 4. 发送消息到 Kafka
     * 5. 返回"处理中"状态
     */
    @Transactional
    public OrderResult processSeckill(SeckillRequest request) {
        Long userId = request.userId();
        Long seckillId = request.seckillId();

        log.info("处理秒杀请求：userId={}, seckillId={}", userId, seckillId);

        // 1. 验证秒杀活动
        Seckill seckill = validateSeckill(seckillId);
        if (seckill == null) {
            return OrderResult.fail("秒杀活动不存在");
        }

        // 2. 检查用户是否重复参与（一人限购）
        if (seckillResultRepository.existsById(userId)) {
            log.info("用户已参与过秒杀：userId={}, seckillId={}", userId, seckillId);
            return OrderResult.fail("您已参与过本次秒杀");
        }

        // 3. Redis 预扣减库存
        if (!redisStockService.tryDecrementStock(seckillId)) {
            log.warn("库存不足：seckillId={}", seckillId);
            return OrderResult.fail("库存不足，已售罄");
        }

        // 4. 保存秒杀结果（处理中状态）
        SeckillResult result = SeckillResult.processing(userId, seckillId);
        seckillResultRepository.save(result);

        // 5. 发送消息到 Kafka
        SeckillMessage message = SeckillMessage.of(userId, seckillId);
        try {
            kafkaTemplate.send(SECKILL_TOPIC, String.valueOf(userId), serializeMessage(message));
            log.info("发送秒杀消息到 Kafka：userId={}, seckillId={}", userId, seckillId);
        } catch (Exception e) {
            log.error("发送 Kafka 消息失败：userId={}, seckillId={}", userId, seckillId, e);
            // 回滚库存
            redisStockService.rollbackStock(seckillId);
            return OrderResult.fail("系统繁忙，请稍后重试");
        }

        // 6. 返回处理中状态
        return OrderResult.processing();
    }

    /**
     * 验证秒杀活动是否有效
     */
    private Seckill validateSeckill(Long seckillId) {
        Seckill seckill = seckillRepository.findById(seckillId).orElse(null);
        if (seckill == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(seckill.getStartTime())) {
            log.warn("秒杀未开始：seckillId={}, startTime={}", seckillId, seckill.getStartTime());
            return null;
        }

        if (now.isAfter(seckill.getEndTime())) {
            log.warn("秒杀已结束：seckillId={}, endTime={}", seckillId, seckill.getEndTime());
            return null;
        }

        return seckill;
    }

    /**
     * 序列化消息
     */
    private String serializeMessage(SeckillMessage message) {
        return String.format("{\"userId\":%d,\"seckillId\":%d,\"timestamp\":\"%s\"}",
                message.userId(),
                message.seckillId(),
                message.timestamp());
    }

    /**
     * 查询秒杀结果
     */
    public SeckillResult getSeckillResult(Long userId) {
        return seckillResultRepository.findById(userId).orElse(null);
    }

    /**
     * 初始化库存（用于测试）
     */
    public void initStock(Long seckillId) {
        Seckill seckill = seckillRepository.findById(seckillId)
                .orElseThrow(() -> new IllegalArgumentException("秒杀活动不存在"));
        redisStockService.preloadStock(seckillId, seckill.getStock());
        log.info("初始化库存：seckillId={}, stock={}", seckillId, seckill.getStock());
    }
}
