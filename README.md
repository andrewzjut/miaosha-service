# 秒杀系统 - Seckill System

基于分布式锁和消息队列的高并发秒杀系统。

## 技术栈

- **语言**: Java 17
- **框架**: Spring Boot 3.4.3
- **数据库**: PostgreSQL
- **缓存**: Redis + Redisson
- **消息队列**: Kafka
- **构建工具**: Maven

## 核心特性

- ✅ **高并发支持**: Redis 预扣减库存，抗 10 万 + QPS
- ✅ **防超卖**: 分布式锁 + 数据库乐观锁双重保证
- ✅ **削峰填谷**: Kafka 消息队列异步处理订单
- ✅ **库存一致性**: Redis 与数据库最终一致性
- ✅ **可查询**: 支持前端轮询查询秒杀结果

## 快速开始

### 环境要求

- Java 17+
- PostgreSQL 15+
- Redis 7+
- Kafka 3.8+

### 启动基础设施

```bash
docker-compose up -d postgres redis kafka
```

### 配置数据库

```bash
# 创建数据库
createdb -h localhost -U postgres seckill

# 应用会自动运行 Flyway 迁移
```

### 运行应用

```bash
mvn spring-boot:run
```

### 运行测试

```bash
# 单元测试
mvn test

# 压力测试
mvn test -Dtest=StressTest
```

## API 接口

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/seckill/{id}` | POST | 提交秒杀请求 |
| `/api/seckill/{id}/result` | GET | 查询秒杀结果 |
| `/api/seckill/{id}` | GET | 查询秒杀活动详情 |
| `/api/seckill` | GET | 查询秒杀活动列表 |

### 请求示例

```bash
# 提交秒杀请求
curl -X POST "http://localhost:8080/api/seckill/1?userId=1000"

# 查询秒杀结果
curl "http://localhost:8080/api/seckill/1/result?userId=1000"
```

## 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                         用户层                               │
│                    (Controller)                              │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                       业务服务层                             │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐      │
│  │SeckillService│    │OrderService │    │InventoryService│  │
│  └─────────────┘    └─────────────┘    └─────────────┘      │
└─────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│   Redis 缓存     │ │  Kafka 消息队列   │ │  PostgreSQL     │
│  (库存预扣减)    │ │  (异步下单)     │ │  (持久化)       │
└─────────────────┘ └─────────────────┘ └─────────────────┘
```

## 核心流程

```
用户请求 → Redis 预扣减 → 成功 → 发送 Kafka → 返回"处理中"
                            │
                            ▼
                    消费者监听 → 分布式锁 → 扣减数据库库存 → 创建订单
```

## 测试报告

### 压力测试结果 (200 并发抢 100 库存)

| 指标 | 结果 |
|------|------|
| 总库存 | 100 |
| 并发请求数 | 200 |
| 成功请求数 | 100 |
| 失败请求数 | 100 |
| 实际订单数 | 100 |
| 超卖 | ❌ 无 |

### 验证点

- ✅ 订单数 ≤ 库存数（无超卖）
- ✅ Redis 和数据库库存一致
- ✅ 分布式锁保证并发安全

## 项目结构

```
src/
├── main/
│   ├── java/com/example/demo/
│   │   ├── controller/        # 控制器层
│   │   ├── service/           # 业务服务层
│   │   ├── domain/            # 领域实体
│   │   ├── dto/               # 数据传输对象
│   │   ├── repository/        # 数据访问层
│   │   ├── infrastructure/    # 基础设施
│   │   └── exception/         # 异常处理
│   └── resources/
│       ├── db/migration/      # Flyway 迁移脚本
│       └── application.yml    # 配置文件
└── test/
    └── java/com/example/demo/
        ├── StressTest.java    # 压力测试
        └── service/           # 单元测试
```

## 相关文档

- [架构设计文档](docs/architecture.md)
- [设计文档](docs/design.md)

## License

MIT
