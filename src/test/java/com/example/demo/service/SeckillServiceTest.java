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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.example.demo.infrastructure.KafkaConfig.SECKILL_TOPIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

/**
 * 秒杀服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class SeckillServiceTest {

    @Mock
    private SeckillRepository seckillRepository;

    @Mock
    private RedisStockService redisStockService;

    @Mock
    private RedisResultService redisResultService;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private SeckillResultRepository seckillResultRepository;

    @Mock
    private CompletableFuture<SendResult<String, String>> sendFuture;

    private SeckillService seckillService;

    private Seckill testSeckill;
    private SeckillRequest testRequest;

    @BeforeEach
    void setUp() {
        seckillService = new SeckillService(
                seckillRepository,
                redisStockService,
                redisResultService,
                kafkaTemplate,
                seckillResultRepository
        );

        // 准备测试数据
        testSeckill = new Seckill(
                1L,
                "iPhone 15 Pro",
                100,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(1)
        );

        testRequest = new SeckillRequest(100L, 1L);
    }

    @Test
    @DisplayName("秒杀成功 - 库存充足且活动有效")
    void processSeckill_Success() {
        // Given
        given(seckillRepository.findById(1L)).willReturn(Optional.of(testSeckill));
        given(seckillResultRepository.existsById(100L)).willReturn(false);
        given(redisStockService.tryDecrementStock(1L)).willReturn(true);
        given(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .willReturn(sendFuture);

        // When
        OrderResult result = seckillService.processSeckill(testRequest);

        // Then
        assertThat(result.success()).isFalse(); // 处理中状态
        assertThat(result.message()).isEqualTo("处理中");

        // 验证 Redis 库存扣减
        verify(redisStockService).tryDecrementStock(1L);

        // 验证 Kafka 消息发送
        verify(kafkaTemplate).send(eq(SECKILL_TOPIC), eq("100"), anyString());

        // 验证保存了秒杀结果
        verify(seckillResultRepository).save(any(SeckillResult.class));
    }

    @Test
    @DisplayName("秒杀失败 - 活动不存在")
    void processSeckill_ActivityNotFound() {
        // Given
        given(seckillRepository.findById(1L)).willReturn(Optional.empty());

        // When
        OrderResult result = seckillService.processSeckill(testRequest);

        // Then
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("不存在");

        // 验证没有扣减库存
        verify(redisStockService, times(0)).tryDecrementStock(anyLong());
    }

    @Test
    @DisplayName("秒杀失败 - 用户重复参与")
    void processSeckill_UserAlreadyParticipated() {
        // Given
        given(seckillRepository.findById(1L)).willReturn(Optional.of(testSeckill));
        given(seckillResultRepository.existsById(100L)).willReturn(true);

        // When
        OrderResult result = seckillService.processSeckill(testRequest);

        // Then
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("已参与");

        // 验证没有扣减库存
        verify(redisStockService, times(0)).tryDecrementStock(anyLong());
    }

    @Test
    @DisplayName("秒杀失败 - 库存不足")
    void processSeckill_StockNotEnough() {
        // Given
        given(seckillRepository.findById(1L)).willReturn(Optional.of(testSeckill));
        given(seckillResultRepository.existsById(100L)).willReturn(false);
        given(redisStockService.tryDecrementStock(1L)).willReturn(false);

        // When
        OrderResult result = seckillService.processSeckill(testRequest);

        // Then
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("库存");

        // 验证没有发送 Kafka 消息
        verify(kafkaTemplate, times(0)).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("秒杀失败 - 发送 Kafka 消息失败，回滚库存")
    void processSeckill_KafkaSendFailure_Rollback() {
        // Given
        given(seckillRepository.findById(1L)).willReturn(Optional.of(testSeckill));
        given(seckillResultRepository.existsById(100L)).willReturn(false);
        given(redisStockService.tryDecrementStock(1L)).willReturn(true);
        given(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .willThrow(new RuntimeException("Kafka 发送失败"));

        // When
        OrderResult result = seckillService.processSeckill(testRequest);

        // Then
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("系统繁忙");

        // 验证回滚了库存
        verify(redisStockService).rollbackStock(1L);
    }

    @Test
    @DisplayName("查询秒杀结果 - 成功")
    void getSeckillResult_Success() {
        // Given
        SeckillResult expected = new SeckillResult(100L, 1L, true);
        expected.setOrderId(12345L);
        expected.setMessage("秒杀成功");

        given(seckillResultRepository.findById(100L)).willReturn(Optional.of(expected));

        // When
        SeckillResult result = seckillService.getSeckillResult(100L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getOrderId()).isEqualTo(12345L);
        assertThat(result.getMessage()).isEqualTo("秒杀成功");
    }

    @Test
    @DisplayName("查询秒杀结果 - 未找到")
    void getSeckillResult_NotFound() {
        // Given
        given(seckillResultRepository.findById(100L)).willReturn(Optional.empty());

        // When
        SeckillResult result = seckillService.getSeckillResult(100L);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("初始化库存 - 成功")
    void initStock_Success() {
        // Given
        given(seckillRepository.findById(1L)).willReturn(Optional.of(testSeckill));

        // When
        seckillService.initStock(1L);

        // Then
        verify(redisStockService).preloadStock(1L, 100);
    }
}
