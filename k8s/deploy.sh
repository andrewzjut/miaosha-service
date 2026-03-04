#!/bin/bash
# Seckill Application Deployment Script for Mac Apple Silicon
# 这个脚本将部署秒杀系统到本地 Kubernetes 集群

set -e

echo "============================================"
echo "  部署秒杀系统到 Kubernetes (Apple Silicon)"
echo "============================================"

NAMESPACE="seckill"

echo ""
echo ">>> 1. 检查 Kubernetes 连接..."
kubectl cluster-info | head -2

# 2. 构建 Docker 镜像
echo ""
echo ">>> 2. 构建 Docker 镜像..."
docker build --platform linux/arm64 -t seckill-app:latest .
echo "Docker 镜像已准备就绪：seckill-app:latest"

# 3. 创建命名空间
echo ""
echo ">>> 3. 创建命名空间..."
kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -

# 4. 创建 Secret 和 ConfigMap
echo ""
echo ">>> 4. 创建 Secret 和 ConfigMap..."
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/configmap.yaml

# 5. 创建数据库迁移 ConfigMap
echo ""
echo ">>> 5. 创建数据库迁移脚本..."
kubectl create configmap seckill-migrations \
  --from-file=src/main/resources/db/migration/V1__init_schema.sql \
  --from-file=src/main/resources/db/migration/V2__add_inventory_table.sql \
  --namespace $NAMESPACE \
  --dry-run=client -o yaml | kubectl apply -f -

# 6. 创建 ServiceAccount 和 RBAC
echo ""
echo ">>> 6. 创建 ServiceAccount 和 RBAC..."
kubectl apply -f k8s/rbac.yaml

# 7. 部署基础设施（PostgreSQL, Redis, Kafka）
echo ""
echo ">>> 7. 部署基础设施..."
echo "    - 部署 PostgreSQL..."
kubectl apply -f k8s/postgres.yaml

echo "    - 部署 Redis..."
kubectl apply -f k8s/redis.yaml

echo "    - 部署 Kafka + Zookeeper..."
kubectl apply -f k8s/kafka.yaml

# 8. 等待基础设施就绪
echo ""
echo ">>> 8. 等待基础设施就绪（最多 180 秒）..."

echo "    等待 PostgreSQL 就绪..."
kubectl wait --for=condition=ready pod -l app=seckill-postgres -n $NAMESPACE --timeout=120s || echo "    PostgreSQL 未就绪，继续..."

echo "    等待 Redis 就绪..."
kubectl wait --for=condition=ready pod -l app=seckill-redis -n $NAMESPACE --timeout=60s || echo "    Redis 未就绪，继续..."

echo "    等待 Zookeeper 就绪..."
kubectl wait --for=condition=ready pod -l app=seckill-zookeeper -n $NAMESPACE --timeout=60s || echo "    Zookeeper 未就绪，继续..."

echo "    等待 Kafka 就绪..."
kubectl wait --for=condition=ready pod -l app=seckill-kafka -n $NAMESPACE --timeout=120s || echo "    Kafka 未就绪，继续..."

# 9. 等待并执行数据库迁移
echo ""
echo ">>> 9. 执行数据库迁移..."
sleep 10
kubectl apply -f k8s/postgres.yaml
kubectl wait --for=condition=complete job/create-seckill-tables -n $NAMESPACE --timeout=120s || echo "    迁移可能已经完成或超时"

# 10. 创建 Kafka Topics
echo ""
echo ">>> 10. 创建 Kafka Topics..."
sleep 5
kubectl apply -f k8s/kafka.yaml
kubectl wait --for=condition=complete job/create-kafka-topics -n $NAMESPACE --timeout=60s || echo "    Topic 可能已经创建"

# 11. 部署应用
echo ""
echo ">>> 11. 部署应用..."
kubectl apply -f k8s/deployment.yaml

# 12. 部署 HPA
echo ""
echo ">>> 12. 部署自动扩缩容 (HPA)..."
kubectl apply -f k8s/hpa.yaml

# 13. 等待应用就绪
echo ""
echo ">>> 13. 等待应用 Pod 就绪（最多 180 秒）..."
kubectl wait --for=condition=ready pod -l app=seckill-app -n $NAMESPACE --timeout=180s || echo "    应用未就绪，继续..."

# 14. 显示部署状态
echo ""
echo "============================================"
echo "  部署完成！"
echo "============================================"
echo ""
echo ">>> 服务状态:"
kubectl get pods -n $NAMESPACE
echo ""
echo ">>> 服务列表:"
kubectl get services -n $NAMESPACE
echo ""
echo ">>> 访问方式:"
echo "    - NodePort: http://localhost:30080"
echo "    - ClusterIP: seckill-app-service.seckill.svc.cluster.local:80"
echo ""
echo ">>> 常用命令:"
echo "    # 查看日志"
echo "    kubectl logs -f deployment/seckill-app -n $NAMESPACE"
echo ""
echo "    # 查看事件"
echo "    kubectl get events -n $NAMESPACE --sort-by='.lastTimestamp'"
echo ""
echo "    # 进入 Pod 调试"
echo "    kubectl exec -it deployment/seckill-app -n $NAMESPACE -- /bin/sh"
echo ""
echo "    # 清理所有资源"
echo "    kubectl delete namespace $NAMESPACE"
echo ""
echo "============================================"
