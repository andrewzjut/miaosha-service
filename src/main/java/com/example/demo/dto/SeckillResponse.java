package com.example.demo.dto;

import java.time.LocalDateTime;

/**
 * 秒杀响应 DTO
 */
public record SeckillResponse(
    Long id,
    String productName,
    Integer stock,
    LocalDateTime startTime,
    LocalDateTime endTime,
    boolean isActive,
    String status
) {
    public static SeckillResponse fromSeckill(com.example.demo.domain.Seckill seckill) {
        String status;
        if (seckill.isEnded()) {
            status = "已结束";
        } else if (seckill.isStarted()) {
            status = "进行中";
        } else {
            status = "未开始";
        }

        return new SeckillResponse(
            seckill.getId(),
            seckill.getProductName(),
            seckill.getStock(),
            seckill.getStartTime(),
            seckill.getEndTime(),
            seckill.isActive(),
            status
        );
    }
}
