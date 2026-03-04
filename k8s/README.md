# Kubernetes 部署指南

## 概述

本文档描述如何将秒杀系统部署到本地 Kubernetes 集群（Docker Desktop for Mac - Apple Silicon）。

## 前置条件

- Docker Desktop for Mac（启用 Kubernetes）
- kubectl 命令行工具
- 本地已构建的 jar 包（`target/demo-0.0.1-SNAPSHOT.jar`）

## 架构组件

| 组件 | 名称 | 端口 | 说明 |
|------|------|------|------|
| PostgreSQL | seckill-postgres | 5432 | 数据库 |
| Redis | seckill-redis | 6379 | 缓存 |
| Zookeeper | seckill-zookeeper | 2181 | Kafka 依赖 |
| Kafka | seckill-kafka | 9092 | 消息队列 |
| Application | seckill-app | 8080 | 应用服务 |

## 快速部署

### 方式一：一键部署脚本

```bash
# 执行部署脚本
./k8s/deploy.sh
```

### 方式二：手动部署

```bash
# 1. 构建 Docker 镜像
docker build --platform linux/arm64 -t seckill-app:latest .

# 2. 创建命名空间
kubectl create namespace seckill

# 3. 创建 Secret 和 ConfigMap
kubectl apply -f k8s/secrets.yaml -f k8s/configmap.yaml

# 4. 创建 RBAC
kubectl apply -f k8s/rbac.yaml

# 5. 创建数据库迁移脚本
kubectl create configmap seckill-migrations \
  --from-file=src/main/resources/db/migration/V1__init_schema.sql \
  --from-file=src/main/resources/db/migration/V2__add_inventory_table.sql \
  -n seckill

# 6. 部署基础设施
kubectl apply -f k8s/postgres.yaml
kubectl apply -f k8s/redis.yaml
kubectl apply -f k8s/kafka.yaml

# 7. 等待基础设施就绪
kubectl wait --for=condition=ready pod -l app=seckill-postgres -n seckill --timeout=120s
kubectl wait --for=condition=ready pod -l app=seckill-redis -n seckill --timeout=60s
kubectl wait --for=condition=ready pod -l app=seckill-kafka -n seckill --timeout=120s

# 8. 部署应用
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/hpa.yaml

# 9. 等待应用就绪
kubectl wait --for=condition=ready pod -l app=seckill-app -n seckill --timeout=180s
```

## 访问服务

### NodePort 访问（推荐）

```bash
# 通过 NodePort 访问
curl http://localhost:30080/api/seckill
```

### 端口转发

```bash
# 端口转发到本地
kubectl port-forward svc/seckill-app-nodeport 8080:8080 -n seckill

# 然后访问
curl http://localhost:8080/api/seckill
```

## API 测试

```bash
# 查询秒杀活动列表
curl http://localhost:30080/api/seckill

# 提交秒杀请求
curl -X POST "http://localhost:30080/api/seckill/1?userId=1000"

# 查询秒杀结果
curl "http://localhost:30080/api/seckill/1/result?userId=1000"
```

## 监控和日志

### 查看 Pod 状态

```bash
kubectl get pods -n seckill
```

### 查看应用日志

```bash
# 查看所有副本日志
kubectl logs -f deployment/seckill-app -n seckill

# 查看特定 Pod 日志
kubectl logs -f seckill-app-xxx -n seckill
```

### 查看事件

```bash
kubectl get events -n seckill --sort-by='.lastTimestamp'
```

## 清理资源

### 方式一：删除整个命名空间

```bash
kubectl delete namespace seckill
```

### 方式二：使用清理脚本

```bash
./k8s/cleanup.sh
```

## 故障排查

### 应用启动失败

```bash
# 查看日志
kubectl logs -f deployment/seckill-app -n seckill

# 查看事件
kubectl describe deployment seckill-app -n seckill
```

### 数据库连接失败

```bash
# 检查 PostgreSQL 状态
kubectl get pods -l app=seckill-postgres -n seckill

# 查看 PostgreSQL 日志
kubectl logs seckill-postgres-0 -n seckill
```

### Kafka 连接失败

```bash
# 检查 Kafka 状态
kubectl get pods -l app=seckill-kafka -n seckill

# 查看 Kafka 日志
kubectl logs seckill-kafka-0 -n seckill
```

## 资源配置

### 应用资源限制

| 资源 | Request | Limit |
|------|---------|-------|
| CPU | 250m | 1000m |
| 内存 | 512Mi | 1Gi |

### 自动扩缩容 (HPA)

| 指标 | 目标值 |
|------|--------|
| CPU 使用率 | 70% |
| 内存使用率 | 80% |
| 最小副本数 | 2 |
| 最大副本数 | 20 |

## K8s 文件清单

| 文件 | 说明 |
|------|------|
| `namespace.yaml` | 命名空间定义 |
| `secrets.yaml` | 数据库连接密钥 |
| `configmap.yaml` | 应用配置 |
| `rbac.yaml` | ServiceAccount 和权限 |
| `postgres.yaml` | PostgreSQL StatefulSet 和 Job |
| `redis.yaml` | Redis StatefulSet |
| `kafka.yaml` | Kafka + Zookeeper StatefulSet |
| `deployment.yaml` | 应用 Deployment 和 Service |
| `hpa.yaml` | 自动扩缩容配置 |
| `deploy.sh` | 部署脚本 |
| `cleanup.sh` | 清理脚本 |
