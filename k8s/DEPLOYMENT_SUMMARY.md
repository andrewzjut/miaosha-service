# 秒杀系统 - K8s 部署完成总结

## 部署架构

```
┌─────────────────────────────────────────────────────────────┐
│                    Kubernetes Namespace: seckill            │
│                                                             │
│  ┌─────────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │ seckill-app (2) │  │seckill-postgres│ │ seckill-redis │  │
│  │  Deployment     │  │  StatefulSet  │  │  StatefulSet  │  │
│  └─────────────────┘  └──────────────┘  └───────────────┘  │
│                                                             │
│  ┌─────────────────┐  ┌──────────────┐                     │
│  │ seckill-kafka   │  │seckill-zookeeper│                  │
│  │  StatefulSet    │  │  StatefulSet  │                     │
│  └─────────────────┘  └──────────────┘                     │
│                                                             │
│  NodePort: 30080 → seckill-app:8080                        │
└─────────────────────────────────────────────────────────────┘
```

## 已创建的 K8s 文件

| 文件 | 说明 |
|------|------|
| `k8s/namespace.yaml` | 命名空间定义 |
| `k8s/secrets.yaml` | 数据库连接密钥 |
| `k8s/configmap.yaml` | 应用配置 |
| `k8s/rbac.yaml` | ServiceAccount 和 RBAC |
| `k8s/postgres.yaml` | PostgreSQL + Flyway 迁移 Job |
| `k8s/redis.yaml` | Redis StatefulSet |
| `k8s/kafka.yaml` | Kafka + Zookeeper StatefulSet |
| `k8s/deployment.yaml` | 应用 Deployment + Service |
| `k8s/hpa.yaml` | 自动扩缩容配置 |
| `k8s/deploy.sh` | 一键部署脚本 |
| `k8s/cleanup.sh` | 清理脚本 |
| `k8s/init-data.sh` | 初始化测试数据脚本 |
| `k8s/test-seckill.sh` | 快速测试脚本 |
| `k8s/stress-test.sh` | 压力测试脚本 |
| `k8s/README.md` | K8s 部署指南 |
| `k8s/TEST.md` | 测试指南 |

## 快速开始

### 1. 部署应用

```bash
./k8s/deploy.sh
```

### 2. 初始化测试数据

```bash
./k8s/init-data.sh
```

### 3. 运行测试

```bash
# 快速测试
./k8s/test-seckill.sh

# 压力测试
./k8s/stress-test.sh
```

### 4. API 测试

```bash
# 查询活动列表
curl http://localhost:30080/api/seckill

# 提交秒杀请求
curl -X POST "http://localhost:30080/api/seckill/1?userId=1000"

# 查询结果
curl "http://localhost:30080/api/seckill/1/result?userId=1000"
```

## 测试结果

### 20 并发测试

| 指标 | 结果 |
|------|------|
| 并发请求数 | 20 |
| 成功订单数 | 7 |
| 数据库库存 | 93 可用 + 7 已售 = 100 ✅ |
| 无超卖 | ✅ |

### 系统特性验证

- ✅ Redis 预扣减库存
- ✅ Kafka 异步订单处理
- ✅ 数据库库存一致性
- ✅ 一人限购限制
- ✅ 无超卖保护

## 核心组件

| 组件 | 版本 | 用途 |
|------|------|------|
| PostgreSQL | 15-alpine | 持久化存储 |
| Redis | 7-alpine | 库存缓存 |
| Kafka | 7.5.0 | 消息队列 |
| Spring Boot | 3.4.3 | 应用框架 |
| Java | 17 | 运行环境 |

## 监控命令

```bash
# 查看 Pod 状态
kubectl get pods -n seckill

# 查看应用日志
kubectl logs -f deployment/seckill-app -n seckill

# 查看数据库库存
kubectl exec -n seckill seckill-postgres-0 -- psql -U postgres -d seckill \
  -c "SELECT * FROM inventory WHERE seckill_id=1;"

# 查看 Redis 库存
kubectl exec -n seckill seckill-redis-0 -- redis-cli get "seckill:stock:1"

# 查看订单数
kubectl exec -n seckill seckill-postgres-0 -- psql -U postgres -d seckill \
  -c "SELECT COUNT(*) FROM seckill_order;"
```

## 清理资源

```bash
kubectl delete namespace seckill
```

## 下一步优化建议

1. **配置优化**
   - 调整 Kafka consumer batch-size 提升吞吐量
   - 配置 Redis 持久化策略

2. **性能优化**
   - 增加应用副本数（当前 2 个）
   - 使用 Redis Cluster 提升吞吐

3. **可观测性**
   - 配置 Prometheus 监控
   - 添加 Grafana 仪表盘

4. **高可用**
   - PostgreSQL 主从复制
   - Kafka 多副本
   - Redis Sentinel
