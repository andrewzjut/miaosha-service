# 秒杀系统测试指南

## 测试脚本说明

本项目提供了三个测试脚本用于测试部署在 K8s 中的秒杀系统：

### 1. 初始化数据脚本 (init-data.sh)

用于初始化测试数据到数据库：

```bash
./k8s/init-data.sh
```

**执行内容**：
- 向 seckill 表插入测试活动
- 向 inventory 表插入初始库存
- 向 Redis 预加载库存

### 2. 快速测试脚本 (test-seckill.sh)

快速功能测试，验证基本流程：

```bash
# 使用默认配置
./k8s/test-seckill.sh

# 自定义配置
CONCURRENT_USERS=100 SECKILL_ID=1 ./k8s/test-seckill.sh
```

**配置参数**：
| 参数 | 默认值 | 说明 |
|------|--------|------|
| BASE_URL | http://localhost:30080 | 服务地址 |
| SECKILL_ID | 1 | 秒杀活动 ID |
| CONCURRENT_USERS | 50 | 并发用户数 |
| STOCK_COUNT | 100 | 库存数量 |

### 3. 压力测试脚本 (stress-test.sh)

高并发压力测试：

```bash
# 使用默认配置
./k8s/stress-test.sh

# 自定义配置
TOTAL_REQUESTS=500 CONCURRENT_LEVEL=50 ./k8s/stress-test.sh
```

**配置参数**：
| 参数 | 默认值 | 说明 |
|------|--------|------|
| TOTAL_REQUESTS | 200 | 总请求数 |
| CONCURRENT_LEVEL | 20 | 并发度 |
| SLEEP_INTERVAL | 0.01 | 请求间隔（秒） |

## 完整测试流程

### 步骤 1: 部署应用

```bash
# 确保 K8s 集群运行正常
kubectl cluster-info

# 部署应用（如果还未部署）
./k8s/deploy.sh
```

### 步骤 2: 初始化测试数据

```bash
./k8s/init-data.sh
```

### 步骤 3: 运行快速测试

```bash
./k8s/test-seckill.sh
```

### 步骤 4: 运行压力测试

```bash
./k8s/stress-test.sh
```

### 步骤 5: 验证结果

```bash
# 查看数据库库存
kubectl exec -n seckill seckill-postgres-0 -- psql -U postgres -d seckill \
  -c "SELECT * FROM inventory WHERE seckill_id = 1;"

# 查看订单数
kubectl exec -n seckill seckill-postgres-0 -- psql -U postgres -d seckill \
  -c "SELECT COUNT(*) FROM seckill_order;"

# 查看 Redis 库存
kubectl exec -n seckill seckill-redis-0 -- redis-cli get "seckill:stock:1"
```

## 手动 API 测试

### 查询秒杀活动列表

```bash
curl http://localhost:30080/api/seckill
```

### 查询秒杀活动详情

```bash
curl http://localhost:30080/api/seckill/1
```

### 提交秒杀请求

```bash
curl -X POST "http://localhost:30080/api/seckill/1?userId=1000"
```

### 查询秒杀结果

```bash
curl "http://localhost:30080/api/seckill/1/result?userId=1000"
```

### 健康检查

```bash
curl http://localhost:30080/actuator/health
```

## 测试数据清理

```bash
# 清理订单和结果数据
kubectl exec -n seckill seckill-postgres-0 -- psql -U postgres -d seckill \
  -c "TRUNCATE seckill_order, seckill_result RESTART IDENTITY CASCADE;"

# 重置库存
kubectl exec -n seckill seckill-postgres-0 -- psql -U postgres -d seckill \
  -c "UPDATE inventory SET available_stock=100, locked_stock=0, sold_stock=0 WHERE seckill_id=1;"

# 重置 Redis 库存
kubectl exec -n seckill seckill-redis-0 -- redis-cli SET "seckill:stock:1" "100"
```

## 预期测试结果

### 快速测试 (50 并发)

- 成功请求数：约 40-50（取决于 Redis 预扣减）
- 订单创建数：取决于 Kafka 消费速度
- 无超卖现象

### 压力测试 (200 请求)

- 总请求数：200
- QPS：50-100（本地 K8s 环境）
- 数据库库存与订单数一致

## 常见问题排查

### 1. 秒杀活动不存在

**症状**：API 返回"秒杀活动不存在"

**解决**：
```bash
./k8s/init-data.sh
```

### 2. Redis 库存为 0

**症状**：所有请求都返回"库存不足"

**解决**：
```bash
kubectl exec -n seckill seckill-redis-0 -- redis-cli SET "seckill:stock:1" "100"
```

### 3. Kafka 消费失败

**症状**：订单数一直是 0，日志显示 "Acknowledgment" 错误

**解决**：
```bash
kubectl rollout restart deployment/seckill-app -n seckill
```

### 4. 服务不可用

**症状**：curl 返回连接错误

**解决**：
```bash
# 检查 Pod 状态
kubectl get pods -n seckill

# 查看日志
kubectl logs -n seckill deployment/seckill-app

# 重启应用
kubectl rollout restart deployment/seckill-app -n seckill
```

## 性能优化建议

1. **增加应用副本**：
   ```bash
   kubectl scale deployment/seckill-app --replicas=5 -n seckill
   ```

2. **调整 JVM 参数**：在 deployment.yaml 中增加堆内存配置

3. **优化 Kafka 批量处理**：调整 consumer batch-size 配置

4. **使用 Redis Cluster**：提升 Redis 吞吐能力
