# 设计文档

## 1. 文档概述

### 1.1 目的

本文档描述秒杀系统的详细设计，包括模块设计、接口设计、数据库设计等，为开发实现提供指导。

### 1.2 范围

- 秒杀活动管理
- 秒杀请求处理
- 订单创建
- 库存管理
- 结果查询

### 1.3 读者对象

- 后端开发人员
- 测试人员
- 运维人员

---

## 2. 需求分析

### 2.1 功能需求

| 需求 ID | 描述 | 优先级 |
|---------|------|--------|
| F1 | 用户可提交秒杀请求 | P0 |
| F2 | 系统验证秒杀活动有效性 | P0 |
| F3 | 系统限制一人限购 | P0 |
| F4 | 系统扣减库存并创建订单 | P0 |
| F5 | 用户可查询秒杀结果 | P0 |
| F6 | 防止超卖 | P0 |

### 2.2 非功能需求

| 需求 ID | 描述 | 指标 |
|---------|------|------|
| NF1 | 并发能力 | 10 万 + QPS |
| NF2 | 响应时间 | P99 < 200ms |
| NF3 | 库存准确 | 零超卖 |
| NF4 | 可用性 | 99.99% |

---

## 3. 详细设计

### 3.1 秒杀请求处理

#### 3.1.1 时序图

```
User -> Controller -> SeckillService -> RedisStockService
                                          │
                                          ▼
                                       Redis
                                          │
                                          ▼
Controller <- SeckillService <- KafkaTemplate
   │
   ▼
User (返回"处理中")
```

#### 3.1.2 伪代码

```java
@Service
public class SeckillService {

    public OrderResult processSeckill(SeckillRequest request) {
        // 1. 验证秒杀活动
        Seckill seckill = validateSeckill(request.seckillId());
        if (seckill == null) {
            return OrderResult.fail("秒杀活动不存在或已结束");
        }

        // 2. 检查用户重复参与
        if (seckillResultRepository.existsById(request.userId())) {
            return OrderResult.fail("您已参与过本次秒杀");
        }

        // 3. Redis 预扣减库存
        if (!redisStockService.tryDecrementStock(request.seckillId())) {
            return OrderResult.fail("库存不足");
        }

        // 4. 保存秒杀结果（处理中）
        SeckillResult result = SeckillResult.processing(
            request.userId(), request.seckillId());
        seckillResultRepository.save(result);

        // 5. 发送 Kafka 消息
        SeckillMessage message = SeckillMessage.of(
            request.userId(), request.seckillId());
        kafkaTemplate.send("seckill-orders",
            String.valueOf(request.userId()),
            serialize(message));

        // 6. 返回处理中
        return OrderResult.processing();
    }
}
```

### 3.2 订单创建

#### 3.2.1 时序图

```
Consumer -> OrderService -> DistributedLockService
                              │
                              ▼
                           Redis (锁)
                              │
                              ▼
OrderService -> InventoryService -> InventoryRepository
       │                                  │
       ▼                                  ▼
OrderService -> SeckillOrderRepository -> PostgreSQL
       │
       ▼
OrderService -> RedisResultService -> Redis (结果缓存)
```

#### 3.2.2 伪代码

```java
@Service
public class OrderService {

    @Transactional
    public OrderResult createOrder(Long userId, Long seckillId) {
        return distributedLockService.executeWithLock(
            "seckill:" + seckillId, 3, 10, () -> {

            // 1. 双重检查：用户是否已参与
            if (seckillResultRepository.existsById(userId)) {
                return OrderResult.fail("您已参与过本次秒杀");
            }

            // 2. 获取库存
            Inventory inventory = inventoryService.getInventory(seckillId);
            if (inventory == null) {
                redisStockService.rollbackStock(seckillId);
                return OrderResult.fail("库存记录不存在");
            }

            // 3. 锁定库存（乐观锁）
            boolean locked = inventoryService.tryLockStock(
                seckillId, 1, inventory.getVersion());
            if (!locked) {
                redisStockService.rollbackStock(seckillId);
                return OrderResult.fail("库存不足");
            }

            // 4. 创建订单
            Long orderId = generateOrderId();
            SeckillOrder order = new SeckillOrder(orderId, userId, seckillId);
            order.markSuccess();
            seckillOrderRepository.save(order);

            // 5. 确认销售（locked -> sold）
            jdbcTemplate.update(
                "UPDATE inventory SET locked_stock = locked_stock - 1, " +
                "sold_stock = sold_stock + 1, version = version + 1 " +
                "WHERE seckill_id = ? AND locked_stock >= 1",
                seckillId);

            // 6. 更新秒杀结果
            updateSeckillResult(userId, seckillId, true, "秒杀成功", orderId);

            return OrderResult.success(orderId);
        });
    }
}
```

### 3.3 库存管理

#### 3.3.1 状态机

```
┌─────────────┐
│ available   │ 可用库存
└──────┬──────┘
       │ tryLockStock
       ▼
┌─────────────┐
│  locked     │ 已锁定库存
└──────┬──────┘
       │ confirmSale
       ▼
┌─────────────┐
│   sold      │ 已售库存
└─────────────┘
```

#### 3.3.2 SQL 语句

```sql
-- 锁定库存
UPDATE inventory
SET available_stock = available_stock - ?,
    locked_stock = locked_stock + ?,
    version = version + 1
WHERE seckill_id = ?
  AND available_stock >= ?
  AND version = ?;

-- 确认销售
UPDATE inventory
SET locked_stock = locked_stock - ?,
    sold_stock = sold_stock + ?,
    version = version + 1
WHERE seckill_id = ?
  AND locked_stock >= ?;
```

---

## 4. 接口设计

### 4.1 REST API

#### 4.1.1 提交秒杀请求

```
POST /api/seckill/{id}
```

**请求参数**:
| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| userId | Long | 是 | 用户 ID |

**响应示例**:
```json
{
  "success": false,
  "message": "处理中",
  "orderId": null
}
```

#### 4.1.2 查询秒杀结果

```
GET /api/seckill/{id}/result?userId={userId}
```

**响应示例 (成功)**:
```json
{
  "success": true,
  "message": "秒杀成功",
  "status": "SUCCESS",
  "orderId": 1234567890
}
```

**响应示例 (处理中)**:
```json
{
  "success": false,
  "message": "处理中",
  "status": "PROCESSING"
}
```

#### 4.1.3 查询秒杀活动详情

```
GET /api/seckill/{id}
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "id": 1,
    "productName": "iPhone 15 Pro",
    "stock": 100,
    "startTime": "2026-03-03T10:00:00",
    "endTime": "2026-03-03T12:00:00",
    "isActive": true,
    "status": "进行中"
  }
}
```

#### 4.1.4 查询秒杀活动列表

```
GET /api/seckill
```

**响应示例**:
```json
{
  "success": true,
  "data": [...],
  "total": 5
}
```

---

## 5. 数据库设计

### 5.1 ER 图

```
┌─────────────┐       ┌─────────────┐
│  seckill    │1──────┤  inventory  │
└─────────────┘       └─────────────┘
       │1                    │1
       │                     │
       │N                    │
┌─────────────┐       ┌─────────────┐
│seckill_order│       │seckill_result│
└─────────────┘       └─────────────┘
```

### 5.2 表结构

详见 [V1__init_schema.sql](../src/main/resources/db/migration/V1__init_schema.sql) 和 [V2__add_inventory_table.sql](../src/main/resources/db/migration/V2__add_inventory_table.sql)

---

## 6. 异常处理

### 6.1 业务异常

| 异常类型 | 错误码 | HTTP 状态码 | 处理策略 |
|----------|--------|-------------|----------|
| BusinessException | BUSINESS_ERROR | 400 | 返回错误信息 |
| IllegalArgumentException | INVALID_ARGUMENT | 400 | 返回参数错误 |
| MethodArgumentNotValidException | VALIDATION_ERROR | 400 | 返回字段错误 |
| Exception | SYSTEM_ERROR | 500 | 返回系统繁忙 |

### 6.2 全局异常处理器

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException e) {
        // 返回业务错误
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(...) {
        // 返回参数验证错误
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        // 返回系统错误
    }
}
```

---

## 7. 测试策略

### 7.1 单元测试

| 类 | 覆盖率要求 | 测试框架 |
|----|------------|----------|
| SeckillService | 80%+ | JUnit 5 + Mockito |
| OrderService | 80%+ | JUnit 5 + Mockito |
| RedisStockService | 80%+ | JUnit 5 + Mockito |
| InventoryService | 80%+ | JUnit 5 + Mockito |

### 7.2 压力测试

**测试场景**:
1. 200 并发抢 100 库存
2. 100 并发抢 50 库存
3. 60 并发抢 30 库存

**验证点**:
- 订单数 ≤ 库存数（无超卖）
- Redis 和数据库库存一致
- 数据库库存状态一致（total = available + locked + sold）

**执行命令**:
```bash
mvn test -Dtest=StressTest
```

---

## 8. 部署说明

### 8.1 环境要求

| 组件 | 版本 | 最小配置 |
|------|------|----------|
| JDK | 17+ | 2 核 4G |
| PostgreSQL | 15+ | 2 核 4G |
| Redis | 7+ | 1 核 2G |
| Kafka | 3.8+ | 2 核 4G |

### 8.2 配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| DB_HOST | localhost | 数据库地址 |
| DB_PORT | 5432 | 数据库端口 |
| REDIS_HOST | localhost | Redis 地址 |
| REDIS_PORT | 6379 | Redis 端口 |
| KAFKA_BOOTSTRAP_SERVERS | localhost:9092 | Kafka 地址 |

### 8.3 启动顺序

1. PostgreSQL
2. Redis
3. Kafka
4. Application

---

## 9. 变更历史

| 版本 | 日期 | 作者 | 变更描述 |
|------|------|------|----------|
| 1.0 | 2026-03-03 | zhangtong | 初始版本 |
| 1.1 | 2026-03-03 | zhangtong | 修复库存确认销售的版本竞争问题 |
