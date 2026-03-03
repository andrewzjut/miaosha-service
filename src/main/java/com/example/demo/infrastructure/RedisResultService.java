package com.example.demo.infrastructure;

import com.example.demo.domain.SeckillResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis 秒杀结果服务
 * 用于缓存秒杀结果，支持前端轮询查询
 */
@Service
public class RedisResultService {

    private static final Logger log = LoggerFactory.getLogger(RedisResultService.class);
    private static final String RESULT_KEY_PREFIX = "seckill:result:";
    private static final long CACHE_EXPIRE_HOURS = 24; // 缓存 24 小时

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisResultService(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 缓存秒杀结果
     */
    public void cacheResult(Long userId, SeckillResult result) {
        String key = RESULT_KEY_PREFIX + userId;
        try {
            String json = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(key, json, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            log.info("缓存秒杀结果：userId={}, success={}", userId, result.getSuccess());
        } catch (JsonProcessingException e) {
            log.error("序列化秒杀结果失败：userId={}", userId, e);
        }
    }

    /**
     * 获取秒杀结果
     */
    public SeckillResult getResult(Long userId) {
        String key = RESULT_KEY_PREFIX + userId;
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }

        try {
            if (value instanceof String str) {
                return objectMapper.readValue(str, SeckillResult.class);
            }
            return (SeckillResult) value;
        } catch (JsonProcessingException e) {
            log.error("反序列化秒杀结果失败：userId={}", userId, e);
            return null;
        }
    }

    /**
     * 检查是否有结果
     */
    public boolean hasResult(Long userId) {
        String key = RESULT_KEY_PREFIX + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 删除结果缓存
     */
    public void deleteResult(Long userId) {
        String key = RESULT_KEY_PREFIX + userId;
        redisTemplate.delete(key);
        log.info("删除结果缓存：userId={}", userId);
    }

    /**
     * 更新秒杀结果为成功
     */
    public void updateSuccess(Long userId, Long orderId) {
        SeckillResult result = getResult(userId);
        if (result != null) {
            result.setSuccess(true);
            result.setOrderId(orderId);
            result.setMessage("秒杀成功");
            cacheResult(userId, result);
        }
    }

    /**
     * 更新秒杀结果为失败
     */
    public void updateFail(Long userId, String message) {
        SeckillResult result = getResult(userId);
        if (result != null) {
            result.setSuccess(false);
            result.setMessage(message);
            cacheResult(userId, result);
        }
    }
}
