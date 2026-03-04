# Seckill Application Cleanup Script
# 这个脚本将清理 Kubernetes 集群中的秒杀系统资源

set -e

echo "============================================"
echo "  清理秒杀系统 Kubernetes 资源"
echo "============================================"

NAMESPACE="seckill"

echo ""
echo ">>> 即将删除命名空间 $NAMESPACE 及其所有资源..."
read -p "确认继续？(y/n): " confirm
if [ "$confirm" != "y" ]; then
  echo "已取消"
  exit 0
fi

# 删除命名空间
kubectl delete namespace $NAMESPACE --ignore-not-found

# 删除 Docker 镜像（可选）
echo ""
echo ">>> 是否删除本地 Docker 镜像？"
read -p "删除 seckill-app:latest 镜像？(y/n): " remove_image
if [ "$remove_image" = "y" ]; then
  docker rmi seckill-app:latest 2>/dev/null || echo "镜像不存在或无法删除"
fi

echo ""
echo "============================================"
echo "  清理完成！"
echo "============================================"
echo ""
echo ">>> 剩余资源检查:"
kubectl get pods -n $NAMESPACE 2>/dev/null || echo "命名空间已删除"
kubectl get services -n $NAMESPACE 2>/dev/null || echo "命名空间已删除"
echo ""
