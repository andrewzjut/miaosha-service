package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 秒杀请求 DTO
 */
public record SeckillRequest(
    @NotNull(message = "用户 ID 不能为空")
    @Positive(message = "用户 ID 必须大于 0")
    Long userId,

    @NotNull(message = "秒杀活动 ID 不能为空")
    @Positive(message = "秒杀活动 ID 必须大于 0")
    Long seckillId
) {
}
