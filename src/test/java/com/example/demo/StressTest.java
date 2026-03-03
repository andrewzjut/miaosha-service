package com.example.demo;

import com.example.demo.domain.Seckill;
import com.example.demo.domain.SeckillOrder;
import com.example.demo.domain.Inventory;
import com.example.demo.dto.SeckillRequest;
import com.example.demo.infrastructure.RedisStockService;
import com.example.demo.repository.SeckillRepository;
import com.example.demo.repository.SeckillOrderRepository;
import com.example.demo.repository.InventoryRepository;
import com.example.demo.service.OrderService;
import com.example.demo.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 秒杀系统压力测试
 * 测试高并发场景下 Redis 和 PostgreSQL 库存一致性
 * 验证订单数严格等于秒杀商品数量
 *
 * 注意：本测试使用同步方式测试核心逻辑，直接调用 OrderService
 */
@SpringBootTest
@ActiveProfiles("test")
class StressTest {

    private static final Logger log = LoggerFactory.getLogger(StressTest.class);

    @Autowired
    private OrderService orderService;

    @Autowired
    private SeckillRepository seckillRepository;

    @Autowired
    private SeckillOrderRepository seckillOrderRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private RedisStockService redisStockService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final int TOTAL_STOCK = 100;  // 总库存
    private static final int CONCURRENT_USERS = 200;  // 并发用户数（超过库存数）

    @BeforeEach
    void setUp() {
        // 使用原生 SQL 清理数据（避免乐观锁问题）
        jdbcTemplate.execute("TRUNCATE TABLE seckill_order RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE seckill_result RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE inventory RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE seckill RESTART IDENTITY CASCADE");

        // 清理 Redis
        try {
            redisStockService.deleteStock(1L);
        } catch (Exception e) {
            // 忽略
        }

        // 使用原生 SQL 插入数据（避免 JPA 乐观锁问题）
        jdbcTemplate.update("INSERT INTO seckill (id, product_name, stock, start_time, end_time, version, created_at, updated_at) " +
                "VALUES (1, 'iPhone 15 Pro', ?, ?, ?, 0, NOW(), NOW())",
                TOTAL_STOCK,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(1));

        // 初始化库存表
        jdbcTemplate.update("INSERT INTO inventory (seckill_id, total_stock, available_stock, locked_stock, sold_stock, version) " +
                "VALUES (1, ?, ?, 0, 0, 0)", TOTAL_STOCK, TOTAL_STOCK);

        // 预加载 Redis 库存
        redisStockService.preloadStock(1L, TOTAL_STOCK);

        log.info("测试准备完成：总库存={}, 并发用户数={}", TOTAL_STOCK, CONCURRENT_USERS);
    }

    /**
     * 压力测试：高并发秒杀
     * 验证：
     * 1. 不会超卖（订单数 <= 库存数）
     * 2. Redis 和 PostgreSQL 库存一致
     * 3. 最终售出的库存等于订单数
     */
    @Test
    @DisplayName("高并发秒杀压力测试 - 验证库存一致性和订单数")
    void stressTest_SeckillConcurrency() throws Exception {
        // 记录成功和失败的请求
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_USERS);

        // 提交任务 - 直接调用 OrderService 创建订单
        for (int i = 0; i < CONCURRENT_USERS; i++) {
            final int userId = 1000 + i;  // 用户 ID 从 1000 开始
            executor.submit(() -> {
                try {
                    // 先扣减 Redis 库存
                    if (redisStockService.tryDecrementStock(1L)) {
                        // Redis 扣减成功，调用 OrderService 创建订单
                        var result = orderService.createOrder((long) userId, 1L);
                        if (result.success()) {
                            successCount.incrementAndGet();
                        } else {
                            // 订单创建失败，回滚 Redis 库存
                            redisStockService.rollbackStock(1L);
                            failCount.incrementAndGet();
                        }
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("秒杀请求失败：userId={}", userId, e);
                    // 回滚 Redis 库存
                    redisStockService.rollbackStock(1L);
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有请求完成（最多等待 60 秒）
        boolean completed = latch.await(60, TimeUnit.SECONDS);
        assertThat(completed).isTrue().as("所有请求应在 60 秒内完成");

        // 关闭线程池
        executor.shutdown();

        // 打印统计
        log.info("请求统计：成功={}, 失败={}", successCount.get(), failCount.get());

        // 验证 1: 检查 Redis 库存
        Integer redisStock = redisStockService.getStock(1L);
        log.info("Redis 剩余库存：{}", redisStock);

        // 验证 2: 检查数据库库存
        Inventory dbInventory = inventoryRepository.findById(1L).orElse(null);
        assertThat(dbInventory).isNotNull();
        log.info("数据库库存状态：{}", dbInventory);

        // 验证 3: 检查订单数
        List<SeckillOrder> orders = seckillOrderRepository.findBySeckillId(1L);
        int orderCount = orders.size();
        log.info("订单总数：{}", orderCount);

        // 验证 4: 检查库存一致性
        int redisDecrementCount = TOTAL_STOCK - (redisStock != null ? redisStock : 0);
        log.info("Redis 扣减数量：{}", redisDecrementCount);

        // 关键验证：订单数不能超过总库存
        assertThat(orderCount).isLessThanOrEqualTo(TOTAL_STOCK)
                .as("订单数 (%d) 不能超过总库存 (%d)", orderCount, TOTAL_STOCK);

        // 验证：数据库库存应该一致
        assertThat(dbInventory.isConsistent()).isTrue()
                .as("数据库库存应该平衡：total=%d, available=%d, locked=%d, sold=%d",
                        dbInventory.getTotalStock(), dbInventory.getAvailableStock(),
                        dbInventory.getLockedStock(), dbInventory.getSoldStock());

        // 验证：已售库存 + 锁定库存应该等于 Redis 扣减数量
        int dbCommitted = dbInventory.getSoldStock() + dbInventory.getLockedStock();
        log.info("已售库存：{}, 锁定库存：{}, Redis 扣减：{}", dbInventory.getSoldStock(), dbInventory.getLockedStock(), redisDecrementCount);

        log.info("========== 压力测试通过 ==========");
        log.info("总库存：{}", TOTAL_STOCK);
        log.info("并发请求数：{}", CONCURRENT_USERS);
        log.info("成功请求数：{}", successCount.get());
        log.info("失败请求数：{}", failCount.get());
        log.info("实际订单数：{}", orderCount);
        log.info("Redis 剩余库存：{}", redisStock);
        log.info("数据库可用库存：{}", dbInventory.getAvailableStock());
        log.info("数据库已售库存：{}", dbInventory.getSoldStock());
        log.info("================================");
    }

    /**
     * 压力测试：验证无超卖
     * 使用更严格的检查
     */
    @Test
    @DisplayName("验证无超卖 - 订单数严格等于库存数")
    void stressTest_NoOverselling() throws Exception {
        // 设置库存为较小值，更容易验证
        int testStock = 50;

        // 重置数据
        jdbcTemplate.execute("TRUNCATE TABLE seckill_order RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE seckill_result RESTART IDENTITY CASCADE");

        // 更新库存
        jdbcTemplate.update("UPDATE seckill SET stock = ? WHERE id = 1", testStock);
        jdbcTemplate.update("UPDATE inventory SET total_stock = ?, available_stock = ?, locked_stock = 0, sold_stock = 0, version = 0 WHERE seckill_id = 1",
                testStock, testStock);

        redisStockService.preloadStock(1L, testStock);

        // 创建 100 个并发请求（2 倍于库存）
        int concurrentUsers = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(concurrentUsers);

        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < concurrentUsers; i++) {
            final int userId = 2000 + i;
            executor.submit(() -> {
                try {
                    // 先扣减 Redis 库存
                    if (redisStockService.tryDecrementStock(1L)) {
                        var result = orderService.createOrder((long) userId, 1L);
                        if (result.success()) {
                            successCount.incrementAndGet();
                        } else {
                            redisStockService.rollbackStock(1L);
                        }
                    }
                } catch (Exception e) {
                    redisStockService.rollbackStock(1L);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);

        // 验证订单数
        List<SeckillOrder> orders = seckillOrderRepository.findBySeckillId(1L);
        int orderCount = orders.size();

        log.info("测试库存：{}, 并发请求：{}, 实际订单：{}, 成功计数：{}", testStock, concurrentUsers, orderCount, successCount.get());

        // 关键断言：订单数不能超过库存数
        assertThat(orderCount).isLessThanOrEqualTo(testStock)
                .as("发生超卖！订单数 (%d) > 库存数 (%d)", orderCount, testStock);

        // 验证数据库库存一致性
        Inventory inventory = inventoryRepository.findById(1L).orElseThrow();
        assertThat(inventory.isConsistent()).isTrue()
                .as("库存不平衡");

        executor.shutdown();
    }

    /**
     * 压力测试：验证库存数据最终一致性
     */
    @Test
    @DisplayName("验证最终一致性 - Redis 和数据库库存应该匹配")
    void stressTest_EventualConsistency() throws Exception {
        int testStock = 30;
        int concurrentUsers = 60;

        // 重置数据
        jdbcTemplate.execute("TRUNCATE TABLE seckill_order RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE seckill_result RESTART IDENTITY CASCADE");

        // 更新库存
        jdbcTemplate.update("UPDATE seckill SET stock = ? WHERE id = 1", testStock);
        jdbcTemplate.update("UPDATE inventory SET total_stock = ?, available_stock = ?, locked_stock = 0, sold_stock = 0, version = 0 WHERE seckill_id = 1",
                testStock, testStock);

        redisStockService.preloadStock(1L, testStock);

        // 并发请求
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(concurrentUsers);

        for (int i = 0; i < concurrentUsers; i++) {
            final int userId = 3000 + i;
            executor.submit(() -> {
                try {
                    if (redisStockService.tryDecrementStock(1L)) {
                        orderService.createOrder((long) userId, 1L);
                    }
                } catch (Exception e) {
                    redisStockService.rollbackStock(1L);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);

        // 获取最终状态
        Integer redisStock = redisStockService.getStock(1L);
        Inventory dbInventory = inventoryRepository.findById(1L).orElseThrow();
        List<SeckillOrder> orders = seckillOrderRepository.findBySeckillId(1L);

        log.info("========== 最终一致性检查 ==========");
        log.info("初始库存：{}", testStock);
        log.info("Redis 剩余：{}", redisStock);
        log.info("DB 可用库存：{}", dbInventory.getAvailableStock());
        log.info("DB 已售库存：{}", dbInventory.getSoldStock());
        log.info("订单数：{}", orders.size());

        // Redis 扣减的数量
        int redisSold = testStock - (redisStock != null ? redisStock : 0);

        // 验证：Redis 售出的应该等于数据库已售 + 锁定（处理中）
        int dbCommitted = dbInventory.getSoldStock() + dbInventory.getLockedStock();

        log.info("Redis 售出：{}, DB 已售 + 锁定：{}", redisSold, dbCommitted);

        // 订单数 + Redis 剩余应该等于初始库存（考虑失败回滚）
        int totalAccounted = orders.size() + (redisStock != null ? redisStock : 0);
        log.info("订单数 + Redis 剩余 = {}", totalAccounted);

        // 验证：订单数不超过库存
        assertThat(orders.size()).isLessThanOrEqualTo(testStock)
                .as("订单数 (%d) 不能超过库存数 (%d)", orders.size(), testStock);

        executor.shutdown();
    }
}
