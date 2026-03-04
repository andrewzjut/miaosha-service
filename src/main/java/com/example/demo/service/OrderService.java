package com.example.demo.service;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 订单服务
 * 负责创建订单、扣减库存
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final SeckillRepository seckillRepository;
    private final SeckillOrderRepository seckillOrderRepository;
    private final SeckillResultRepository seckillResultRepository;
    private final RedisStockService redisStockService;
    private final RedisResultService redisResultService;
    private final DistributedLockService distributedLockService;
    private final InventoryService inventoryService;
    private final JdbcTemplate jdbcTemplate;

    public OrderService(SeckillRepository seckillRepository,
                        SeckillOrderRepository seckillOrderRepository,
                        SeckillResultRepository seckillResultRepository,
                        RedisStockService redisStockService,
                        RedisResultService redisResultService,
                        DistributedLockService distributedLockService,
                        InventoryService inventoryService,
                        JdbcTemplate jdbcTemplate) {
        this.seckillRepository = seckillRepository;
        this.seckillOrderRepository = seckillOrderRepository;
        this.seckillResultRepository = seckillResultRepository;
        this.redisStockService = redisStockService;
        this.redisResultService = redisResultService;
        this.distributedLockService = distributedLockService;
        this.inventoryService = inventoryService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 创建秒杀订单
     * 使用分布式锁保证同一秒杀活动的并发安全
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderResult createOrder(Long userId, Long seckillId) {
        String lockKey = "seckill:" + seckillId;

        return distributedLockService.executeWithLock(lockKey, 3, 10, () -> {
            // 1. 双重检查：检查用户是否已成功参与（只拦截真正抢购成功的用户）
            if (seckillResultRepository.existsSuccessfulBySeckillId(userId, seckillId)) {
                log.info("用户已成功参与秒杀：userId={}, seckillId={}", userId, seckillId);
                return OrderResult.fail("您已参与过本次秒杀");
            }

            // 2. 检查秒杀活动是否存在
            Seckill seckill = seckillRepository.findById(seckillId).orElse(null);
            if (seckill == null) {
                log.warn("秒杀活动不存在：seckillId={}", seckillId);
                // 回滚 Redis 库存
                redisStockService.rollbackStock(seckillId);
                updateSeckillResult(userId, seckillId, false, "秒杀活动不存在", null);
                return OrderResult.fail("秒杀活动不存在");
            }

            // 3. 检查秒杀时间
            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(seckill.getStartTime()) || now.isAfter(seckill.getEndTime())) {
                log.warn("秒杀活动已过期：seckillId={}", seckillId);
                redisStockService.rollbackStock(seckillId);
                updateSeckillResult(userId, seckillId, false, "秒杀活动已过期", null);
                return OrderResult.fail("秒杀活动已过期");
            }

            // 4. 数据库扣减库存（使用 Inventory 表）
            // 先获取当前库存版本号
            var inventory = inventoryService.getInventory(seckillId);
            if (inventory == null) {
                log.warn("库存记录不存在：seckillId={}", seckillId);
                redisStockService.rollbackStock(seckillId);
                updateSeckillResult(userId, seckillId, false, "库存记录不存在", null);
                return OrderResult.fail("库存记录不存在");
            }

            boolean stockLocked = inventoryService.tryLockStock(seckillId, 1, inventory.getVersion());
            if (!stockLocked) {
                log.warn("锁定库存失败：seckillId={}", seckillId);
                // Redis 回滚
                redisStockService.rollbackStock(seckillId);
                updateSeckillResult(userId, seckillId, false, "库存不足", null);
                return OrderResult.fail("库存不足");
            }

            // 5. 创建订单
            Long orderId = generateOrderId();
            SeckillOrder order = new SeckillOrder(orderId, userId, seckillId);
            order.markSuccess();
            seckillOrderRepository.save(order);

            log.info("订单创建成功：orderId={}, userId={}, seckillId={}", orderId, userId, seckillId);

            // 6. 确认销售（从锁定转为已售）- 直接在当前事务中操作
            // 由于 tryLockStock 和 confirmSale 在同一个分布式锁内执行，且 OrderService 有事务注解
            // 我们需要确保使用的是最新数据。简单做法：直接用 SQL 更新，不检查版本号
            // 因为分布式锁已经保证了并发性，不需要再使用乐观锁
            jdbcTemplate.update("UPDATE inventory SET locked_stock = locked_stock - 1, " +
                    "sold_stock = sold_stock + 1, version = version + 1 " +
                    "WHERE seckill_id = ? AND locked_stock >= 1", seckillId);

            // 7. 更新秒杀结果
            updateSeckillResult(userId, seckillId, true, "秒杀成功", orderId);

            return OrderResult.success(orderId);
        });
    }

    /**
     * 更新秒杀结果
     */
    private void updateSeckillResult(Long userId, Long seckillId, boolean success, String message, Long orderId) {
        // 更新数据库
        SeckillResult result = seckillResultRepository.findById(userId).orElse(new SeckillResult(userId, seckillId, success));
        result.setSuccess(success);
        result.setMessage(message);
        result.setOrderId(orderId);
        result.setSeckillId(seckillId);
        seckillResultRepository.save(result);

        // 更新 Redis 缓存
        redisResultService.cacheResult(userId, result);
    }

    /**
     * 生成订单 ID（雪花算法或 UUID）
     */
    private Long generateOrderId() {
        // 简化实现：使用时间戳 + 随机数
        // 生产环境应使用雪花算法
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    /**
     * 查询订单
     */
    public SeckillOrder getOrder(Long orderId) {
        return seckillOrderRepository.findById(orderId).orElse(null);
    }

    /**
     * 根据用户 ID 和秒杀 ID 查询订单
     */
    public SeckillOrder getUserOrder(Long userId, Long seckillId) {
        return seckillOrderRepository.findByUserIdAndSeckillId(userId, seckillId).orElse(null);
    }
}
