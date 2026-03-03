package com.example.demo.service;

import com.example.demo.domain.Inventory;
import com.example.demo.domain.Seckill;
import com.example.demo.domain.SeckillOrder;
import com.example.demo.domain.SeckillResult;
import com.example.demo.dto.OrderResult;
import com.example.demo.infrastructure.DistributedLockService;
import com.example.demo.infrastructure.RedisResultService;
import com.example.demo.infrastructure.RedisStockService;
import com.example.demo.repository.SeckillOrderRepository;
import com.example.demo.repository.SeckillRepository;
import com.example.demo.repository.SeckillResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

/**
 * 订单服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private SeckillRepository seckillRepository;

    @Mock
    private SeckillOrderRepository seckillOrderRepository;

    @Mock
    private SeckillResultRepository seckillResultRepository;

    @Mock
    private RedisStockService redisStockService;

    @Mock
    private RedisResultService redisResultService;

    @Mock
    private DistributedLockService distributedLockService;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private OrderService orderService;

    private Seckill testSeckill;
    private Inventory testInventory;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                seckillRepository,
                seckillOrderRepository,
                seckillResultRepository,
                redisStockService,
                redisResultService,
                distributedLockService,
                inventoryService,
                jdbcTemplate
        );

        testSeckill = new Seckill(
                1L,
                "iPhone 15 Pro",
                100,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(1)
        );

        testInventory = new Inventory(1L, 100, 100);
    }

    @Test
    @DisplayName("创建订单成功")
    void createOrder_Success() {
        // Given
        Long userId = 100L;
        Long seckillId = 1L;

        // 模拟分布式锁执行
        willAnswer(invocation -> {
            Supplier<OrderResult> supplier = invocation.getArgument(3);
            // 设置锁内的行为
            given(seckillResultRepository.existsById(userId)).willReturn(false);
            given(seckillRepository.findById(seckillId)).willReturn(Optional.of(testSeckill));
            given(inventoryService.getInventory(seckillId)).willReturn(testInventory);
            given(inventoryService.tryLockStock(seckillId, 1, testInventory.getVersion())).willReturn(true);
            given(jdbcTemplate.update(anyString(), eq(seckillId))).willReturn(1); // 确认销售成功
            given(seckillOrderRepository.save(any(SeckillOrder.class))).willAnswer(i -> i.getArgument(0));
            given(seckillResultRepository.save(any(SeckillResult.class))).willAnswer(i -> i.getArgument(0));
            return supplier.get();
        }).given(distributedLockService).executeWithLock(anyString(), anyLong(), anyLong(), (Supplier<OrderResult>) any());

        // When
        OrderResult result = orderService.createOrder(userId, seckillId);

        // Then
        assertThat(result.success()).isTrue();
        assertThat(result.orderId()).isNotNull();
        assertThat(result.message()).isEqualTo("秒杀成功");
    }

    @Test
    @DisplayName("创建订单失败 - 用户已参与")
    void createOrder_UserAlreadyParticipated() {
        // Given
        Long userId = 100L;
        Long seckillId = 1L;

        willAnswer(invocation -> {
            Supplier<OrderResult> supplier = invocation.getArgument(3);
            given(seckillResultRepository.existsById(userId)).willReturn(true);
            return supplier.get();
        }).given(distributedLockService).executeWithLock(anyString(), anyLong(), anyLong(), (Supplier<OrderResult>) any());

        // When
        OrderResult result = orderService.createOrder(userId, seckillId);

        // Then
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("已参与");

        // 验证没有调用库存服务
        verify(inventoryService, times(0)).tryLockStock(anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("创建订单失败 - 库存不足")
    void createOrder_StockNotEnough() {
        // Given
        Long userId = 100L;
        Long seckillId = 1L;

        willAnswer(invocation -> {
            Supplier<OrderResult> supplier = invocation.getArgument(3);
            given(seckillResultRepository.existsById(userId)).willReturn(false);
            given(seckillRepository.findById(seckillId)).willReturn(Optional.of(testSeckill));
            given(inventoryService.getInventory(seckillId)).willReturn(testInventory);
            given(inventoryService.tryLockStock(seckillId, 1, testInventory.getVersion())).willReturn(false);
            return supplier.get();
        }).given(distributedLockService).executeWithLock(anyString(), anyLong(), anyLong(), (Supplier<OrderResult>) any());

        // When
        OrderResult result = orderService.createOrder(userId, seckillId);

        // Then
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("库存");

        // 验证回滚了 Redis 库存
        verify(redisStockService).rollbackStock(seckillId);
    }

    @Test
    @DisplayName("查询订单 - 成功")
    void getOrder_Success() {
        // Given
        Long orderId = 12345L;
        SeckillOrder expected = new SeckillOrder(orderId, 100L, 1L);
        expected.markSuccess();

        given(seckillOrderRepository.findById(orderId)).willReturn(Optional.of(expected));

        // When
        SeckillOrder result = orderService.getOrder(orderId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(orderId);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("查询用户订单")
    void getUserOrder_Success() {
        // Given
        Long userId = 100L;
        Long seckillId = 1L;
        SeckillOrder expected = new SeckillOrder(12345L, userId, seckillId);

        given(seckillOrderRepository.findByUserIdAndSeckillId(userId, seckillId))
                .willReturn(Optional.of(expected));

        // When
        SeckillOrder result = orderService.getUserOrder(userId, seckillId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getSeckillId()).isEqualTo(seckillId);
    }
}
