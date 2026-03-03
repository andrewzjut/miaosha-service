package com.example.demo.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Redis 库存服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class RedisStockServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private RedisStockService redisStockService;

    @BeforeEach
    void setUp() {
        redisStockService = new RedisStockService(redisTemplate);
    }

    @Test
    @DisplayName("预加载库存 - 成功")
    void preloadStock_Success() {
        // Given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        Long seckillId = 1L;
        Integer stock = 100;

        // When
        redisStockService.preloadStock(seckillId, stock);

        // Then
        verify(valueOperations).set(eq("seckill:stock:" + seckillId), eq(stock));
    }

    @Test
    @DisplayName("扣减库存 - 成功")
    void tryDecrementStock_Success() {
        // Given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        Long seckillId = 1L;

        // When
        boolean result = redisStockService.tryDecrementStock(seckillId);

        // Then
        assertThat(result).isTrue();
        verify(valueOperations).decrement(eq("seckill:stock:" + seckillId));
    }

    @Test
    @DisplayName("扣减库存 - 库存不足")
    void tryDecrementStock_StockNotEnough() {
        // Given
        Long seckillId = 1L;
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.decrement(eq("seckill:stock:" + seckillId))).willReturn(-1L);

        // When
        boolean result = redisStockService.tryDecrementStock(seckillId);

        // Then
        assertThat(result).isFalse();
        // 验证回滚
        verify(valueOperations).increment(eq("seckill:stock:" + seckillId));
    }

    @Test
    @DisplayName("回滚库存 - 成功")
    void rollbackStock_Success() {
        // Given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        Long seckillId = 1L;

        // When
        redisStockService.rollbackStock(seckillId);

        // Then
        verify(valueOperations).increment(eq("seckill:stock:" + seckillId));
    }

    @Test
    @DisplayName("获取库存 - 成功")
    void getStock_Success() {
        // Given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        Long seckillId = 1L;
        Integer expectedStock = 50;
        given(valueOperations.get(eq("seckill:stock:" + seckillId))).willReturn(expectedStock);

        // When
        Integer result = redisStockService.getStock(seckillId);

        // Then
        assertThat(result).isEqualTo(expectedStock);
    }

    @Test
    @DisplayName("获取库存 - 不存在")
    void getStock_NotExist() {
        // Given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        Long seckillId = 1L;
        given(valueOperations.get(eq("seckill:stock:" + seckillId))).willReturn(null);

        // When
        Integer result = redisStockService.getStock(seckillId);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("删除库存缓存 - 成功")
    void deleteStock_Success() {
        // Given
        Long seckillId = 1L;

        // When
        redisStockService.deleteStock(seckillId);

        // Then
        verify(redisTemplate).delete(eq("seckill:stock:" + seckillId));
    }

    @Test
    @DisplayName("检查库存缓存是否存在 - 存在")
    void hasStock_Exists() {
        // Given
        Long seckillId = 1L;
        given(redisTemplate.hasKey(eq("seckill:stock:" + seckillId))).willReturn(true);

        // When
        boolean result = redisStockService.hasStock(seckillId);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("检查库存缓存是否存在 - 不存在")
    void hasStock_NotExists() {
        // Given
        Long seckillId = 1L;
        given(redisTemplate.hasKey(eq("seckill:stock:" + seckillId))).willReturn(false);

        // When
        boolean result = redisStockService.hasStock(seckillId);

        // Then
        assertThat(result).isFalse();
    }
}
