package com.example.demo.infrastructure;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁服务
 * 基于 Redisson 实现
 */
@Service
public class DistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockService.class);
    private static final String LOCK_KEY_PREFIX = "lock:";

    private final RedissonClient redissonClient;

    public DistributedLockService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 执行带锁的操作
     *
     * @param key 锁的 key
     * @param waitTime 等待锁的时间（秒）
     * @param leaseTime 锁持有时间（秒），-1 表示使用看门狗自动续期
     * @param action 要执行的操作
     * @return 操作结果
     */
    public <T> T executeWithLock(String key, long waitTime, long leaseTime, Supplier<T> action) {
        String lockKey = LOCK_KEY_PREFIX + key;
        RLock lock = redissonClient.getLock(lockKey);

        boolean locked = false;
        try {
            // 尝试获取锁
            if (leaseTime == -1) {
                // 使用看门狗自动续期
                locked = lock.tryLock(waitTime, TimeUnit.SECONDS);
            } else {
                locked = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);
            }

            if (!locked) {
                log.warn("获取锁超时：key={}", lockKey);
                throw new LockException("获取锁失败，系统繁忙，请稍后重试");
            }

            log.debug("获取锁成功：key={}", lockKey);
            return action.get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取锁被中断：key={}", lockKey, e);
            throw new LockException("获取锁被中断");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("释放锁：key={}", lockKey);
            }
        }
    }

    /**
     * 执行带锁的操作（无返回值）
     */
    public void executeWithLock(String key, long waitTime, long leaseTime, Runnable action) {
        executeWithLock(key, waitTime, leaseTime, () -> {
            action.run();
            return null;
        });
    }

    /**
     * 执行带锁的操作（默认等待时间 1 秒，持有时间 10 秒）
     */
    public <T> T executeWithLock(String key, Supplier<T> action) {
        return executeWithLock(key, 1, 10, action);
    }

    /**
     * 尝试获取锁（不阻塞）
     *
     * @return true 表示获取成功，false 表示获取失败
     */
    public boolean tryLock(String key) {
        String lockKey = LOCK_KEY_PREFIX + key;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            return lock.tryLock(0, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("尝试获取锁被中断：key={}", lockKey, e);
            return false;
        }
    }

    /**
     * 释放锁
     */
    public void unlock(String key) {
        String lockKey = LOCK_KEY_PREFIX + key;
        RLock lock = redissonClient.getLock(lockKey);

        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("释放锁：key={}", lockKey);
        }
    }

    /**
     * 检查锁是否被持有
     */
    public boolean isLocked(String key) {
        String lockKey = LOCK_KEY_PREFIX + key;
        RLock lock = redissonClient.getLock(lockKey);
        return lock.isLocked();
    }

    /**
     * 锁异常
     */
    public static class LockException extends RuntimeException {
        public LockException(String message) {
            super(message);
        }

        public LockException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
