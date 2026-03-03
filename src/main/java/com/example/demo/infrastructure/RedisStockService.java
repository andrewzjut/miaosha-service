package com.example.demo.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis 库存服务
 * 用于库存预扣减，减少数据库压力
 */
@Service
public class RedisStockService {

    private static final Logger log = LoggerFactory.getLogger(RedisStockService.class);
    private static final String STOCK_KEY_PREFIX = "seckill:stock:";
    private static final String STOCK_LOCK_KEY_PREFIX = "seckill:stock:lock:";

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisStockService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 预加载库存到 Redis
     */
    public void preloadStock(Long seckillId, Integer stock) {
        String key = STOCK_KEY_PREFIX + seckillId;
        redisTemplate.opsForValue().set(key, stock);
        log.info("预加载库存到 Redis: seckillId={}, stock={}", seckillId, stock);
    }

    /**
     * 尝试扣减库存（原子操作）
     * @return true 表示扣减成功，false 表示库存不足
     */
    public boolean tryDecrementStock(Long seckillId) {
        String key = STOCK_KEY_PREFIX + seckillId;
        Boolean success = redisTemplate.opsForValue().decrement(key) >= 0;

        if (Boolean.FALSE.equals(success)) {
            // 扣减后为负数，需要回滚
            redisTemplate.opsForValue().increment(key);
            return false;
        }

        return true;
    }

    /**
     * 扣减库存（带锁，防止并发问题）
     */
    public synchronized boolean decrementStockWithLock(Long seckillId) {
        String key = STOCK_KEY_PREFIX + seckillId;
        Integer currentStock = (Integer) redisTemplate.opsForValue().get(key);

        if (currentStock == null || currentStock <= 0) {
            log.warn("库存不足：seckillId={}, currentStock={}", seckillId, currentStock);
            return false;
        }

        redisTemplate.opsForValue().set(key, currentStock - 1);
        log.info("扣减库存成功：seckillId={}, remaining={}", seckillId, currentStock - 1);
        return true;
    }

    /**
     * 回滚库存（当订单创建失败时）
     */
    public void rollbackStock(Long seckillId) {
        String key = STOCK_KEY_PREFIX + seckillId;
        redisTemplate.opsForValue().increment(key);
        log.info("回滚库存：seckillId={}", seckillId);
    }

    /**
     * 获取当前库存
     */
    public Integer getStock(Long seckillId) {
        String key = STOCK_KEY_PREFIX + seckillId;
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? (Integer) value : null;
    }

    /**
     * 删除库存缓存（秒杀结束后）
     */
    public void deleteStock(Long seckillId) {
        String key = STOCK_KEY_PREFIX + seckillId;
        redisTemplate.delete(key);
        log.info("删除库存缓存：seckillId={}", seckillId);
    }

    /**
     * 检查库存缓存是否存在
     */
    public boolean hasStock(Long seckillId) {
        String key = STOCK_KEY_PREFIX + seckillId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
