package com.example.demo.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Kafka 消息体 - 秒杀消息
 */
public record SeckillMessage(
    Long userId,
    Long seckillId,
    LocalDateTime timestamp
) implements Serializable {

    public static SeckillMessage of(Long userId, Long seckillId) {
        return new SeckillMessage(userId, seckillId, LocalDateTime.now());
    }
}
