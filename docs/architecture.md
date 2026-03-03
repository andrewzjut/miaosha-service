# 架构设计文档

## 1. 系统概述

### 1.1 背景

秒杀系统是高并发场景下的典型应用，需要解决以下核心问题：
- **瞬时高并发**: 活动期间 QPS 可达 10 万+
- **库存一致性**: 防止超卖，保证数据准确
- **数据库保护**: 避免数据库被高并发流量击垮
- **用户体验**: 快速响应，结果可查询

### 1.2 设计目标

| 目标 | 指标 |
|------|------|
| 并发能力 | 支持 10 万 + QPS |
| 库存准确 | 零超卖 |
| 响应时间 | P99 < 200ms (第一层返回) |
| 数据库保护 | QPS < 2000 |

---

## 2. 系统架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                         Client Layer                            │
│                      (Web/Mobile App)                           │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                         API Gateway                             │
│                   (Rate Limiting, Auth)                         │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Application Layer                          │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │              SeckillController                          │    │
│  │  - POST /api/seckill/{id}                               │    │
│  │  - GET  /api/seckill/{id}/result                        │    │
│  │  - GET  /api/seckill/{id}                               │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Business Layer                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ SeckillService│  │ OrderService │  │InventoryService│         │
│  │ - 处理秒杀请求│  │ - 创建订单    │  │ - 库存管理    │          │
│  │ - Redis 预扣减 │  │ - 分布式锁    │  │ - 乐观锁更新  │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘
                                │
          ┌─────────────────────┼─────────────────────┐
          ▼                     ▼                     ▼
┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
│    Redis        │   │     Kafka       │   │   PostgreSQL    │
│  - 库存缓存     │   │  - 订单消息     │   │  - 持久化存储    │
│  - 结果缓存     │   │  - 削峰填谷     │   │  - 库存表        │
│  - 分布式锁     │   │  - 异步处理     │   │  - 订单表        │
└─────────────────┘   └─────────────────┘   └─────────────────┘
```

### 2.2 技术选型

| 组件 | 技术 | 选型理由 |
|------|------|----------|
| 应用框架 | Spring Boot 3.4.3 | 成熟稳定，生态完善 |
| 数据库 | PostgreSQL | 性能优秀，支持行级锁 |
| 缓存 | Redis 7 | 高性能，支持原子操作 |
| 分布式锁 | Redisson | 基于 Redis，支持看门狗 |
| 消息队列 | Kafka | 高吞吐，支持持久化 |
| ORM | JPA/Hibernate | 开发效率高 |
| 迁移工具 | Flyway | 版本化管理数据库 |

---

## 3. 核心模块设计

### 3.1 数据模型

#### 3.1.1 秒杀活动表 (seckill)

```sql
CREATE TABLE seckill (
    id BIGINT PRIMARY KEY,
    product_name VARCHAR(200),
    stock INT NOT NULL,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    version INT DEFAULT 0  -- 乐观锁版本号
);
```

#### 3.1.2 库存表 (inventory)

```sql
CREATE TABLE inventory (
    seckill_id BIGINT PRIMARY KEY,
    total_stock INT,        -- 总库存
    available_stock INT,    -- 可用库存
    locked_stock INT,       -- 已锁定库存
    sold_stock INT,         -- 已售库存
    version INT DEFAULT 0   -- 乐观锁版本号
);
```

#### 3.1.3 订单表 (seckill_order)

```sql
CREATE TABLE seckill_order (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    seckill_id BIGINT NOT NULL,
    status SMALLINT DEFAULT 0,  -- 0:处理中 1:成功 2:失败
    UNIQUE (user_id, seckill_id)  -- 一人限购
);
```

#### 3.1.4 秒杀结果表 (seckill_result)

```sql
CREATE TABLE seckill_result (
    user_id BIGINT PRIMARY KEY,
    seckill_id BIGINT,
    success BOOLEAN,
    order_id BIGINT,
    message VARCHAR(500)
);
```

### 3.2 服务层设计

#### 3.2.1 SeckillService

**职责**: 处理秒杀请求的核心入口

**关键方法**:
```java
public OrderResult processSeckill(SeckillRequest request) {
    // 1. 验证秒杀活动
    // 2. 检查用户重复参与
    // 3. Redis 预扣减库存
    // 4. 保存秒杀结果 (处理中)
    // 5. 发送 Kafka 消息
    // 6. 返回"处理中"状态
}
```

#### 3.2.2 OrderService

**职责**: 创建订单，扣减数据库库存

**关键方法**:
```java
@Transactional
public OrderResult createOrder(Long userId, Long seckillId) {
    // 1. 获取分布式锁
    // 2. 双重检查用户参与状态
    // 3. 锁定库存 (available -> locked)
    // 4. 创建订单
    // 5. 确认销售 (locked -> sold)
    // 6. 更新秒杀结果
}
```

#### 3.2.3 InventoryService

**职责**: 管理库存状态

**关键方法**:
```java
@Transactional
public boolean tryLockStock(Long seckillId, Integer quantity, Integer version);
@Transactional
public boolean confirmSale(Long seckillId, Integer quantity, Integer version);
```

### 3.3 基础设施层

#### 3.3.1 RedisStockService

- `preloadStock()`: 预加载库存到 Redis
- `tryDecrementStock()`: 原子扣减库存
- `rollbackStock()`: 回滚库存

#### 3.3.2 DistributedLockService

基于 Redisson 实现：
```java
public <T> T executeWithLock(String key, long waitTime, long leaseTime, Supplier<T> action)
```

#### 3.3.3 SeckillConsumer

Kafka 消费者，异步处理订单：
```java
@KafkaListener(topics = "seckill-orders")
public void consume(String message, Acknowledgment ack)
```

---

## 4. 核心流程

### 4.1 秒杀流程

```
┌─────────┐     ┌──────────────┐     ┌─────────────┐
│  User   │     │SeckillService│     │   Redis     │
└────┬────┘     └──────┬───────┘     └──────┬──────┘
     │                 │                     │
     │ POST /seckill   │                     │
     │────────────────>│                     │
     │                 │                     │
     │                 │ 验证活动/用户        │
     │                 │────────────────────>│
     │                 │                     │
     │                 │ decrement(key)      │
     │                 │────────────────────>│
     │                 │                     │
     │                 │ 成功：发送 Kafka     │
     │                 │────────────────────>│
     │                 │                     │
     │ "处理中"         │                     │
     │<────────────────│                     │
     │                 │                     │
```

### 4.2 订单创建流程

```
┌─────────┐     ┌─────────────┐     ┌───────────┐     ┌──────────┐
│Consumer │     │OrderService │     │ Inventory │     │Database  │
└────┬────┘     └──────┬──────┘     └─────┬─────┘     └────┬─────┘
     │                 │                  │                │
     │ consume()       │                  │                │
     │────────────────>│                  │                │
     │                 │                  │                │
     │                 │ 获取分布式锁      │                │
     │                 │ (lock:seckill:1) │                │
     │                 │                  │                │
     │                 │ 双重检查          │                │
     │                 │                  │                │
     │                 │ lockStock()      │                │
     │                 │(available->locked)                │
     │                 │─────────────────>│                │
     │                 │                  │                │
     │                 │ save(order)      │                │
     │                 │─────────────────────────────────>│
     │                 │                  │                │
     │                 │ confirmSale()    │                │
     │                 │ (locked->sold)   │                │
     │                 │─────────────────>│                │
     │                 │                  │                │
```

---

## 5. 并发控制

### 5.1 多层防护机制

```
第一层：Redis 原子扣减 (高性能过滤)
   │
   ▼
第二层：分布式锁 (Redisson 锁)
   │
   ▼
第三层：数据库乐观锁 (version 字段)
```

### 5.2 锁的粒度

| 锁类型 | Key | 等待时间 | 持有时间 |
|--------|-----|----------|----------|
| 分布式锁 | lock:seckill:{id} | 3 秒 | 10 秒 |

### 5.3 库存状态流转

```
available_stock (可用)
       │
       │ tryLockStock
       ▼
locked_stock (已锁定)
       │
       │ confirmSale
       ▼
sold_stock (已售)
```

---

## 6. 部署架构

### 6.1 Docker Compose

```yaml
services:
  postgres:  # 数据库
  redis:     # 缓存
  kafka:     # 消息队列
  app:       # 应用
```

### 6.2 K8s 配置

- **Deployment**: 2 副本起始
- **HPA**: 根据 CPU/内存/队列长度自动扩缩容 (2-20 副本)
- **Service**: ClusterIP 暴露

---

## 7. 监控与告警

### 7.1 关键指标

- **QPS**: 每秒请求数
- **成功率**: 成功订单数 / 总请求数
- **库存水位**: 可用库存 / 总库存
- **Kafka 积压**: 未消费消息数

### 7.2 Actuator 端点

- `/actuator/health`: 健康检查
- `/actuator/metrics`: 性能指标
- `/actuator/prometheus`: Prometheus 监控

---

## 8. 扩展性考虑

### 8.1 水平扩展

- 应用无状态，可任意扩缩容
- Redis 使用 Cluster 模式
- Kafka 多分区支持并行消费

### 8.2 降级方案

- Redis 故障：降级到数据库扣减（限流）
- Kafka 故障：同步下单（限流）
- 数据库故障：只读缓存（返回"系统繁忙"）
