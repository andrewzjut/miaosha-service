#!/bin/bash
# 初始化秒杀数据到 K8s 数据库

set -e

NAMESPACE="${NAMESPACE:-seckill}"
DB_PASSWORD="postgres123"

echo "============================================"
echo "  初始化秒杀测试数据"
echo "============================================"
echo ""

# 1. 获取 PostgreSQL Pod
echo ">>> 获取 PostgreSQL Pod..."
POSTGRES_POD=$(kubectl get pods -n $NAMESPACE -l app=seckill-postgres -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
if [ -z "$POSTGRES_POD" ]; then
    echo "❌ 未找到 PostgreSQL Pod"
    exit 1
fi
echo "    PostgreSQL Pod: $POSTGRES_POD"

# 2. 获取 Redis Pod
echo ""
echo ">>> 获取 Redis Pod..."
REDIS_POD=$(kubectl get pods -n $NAMESPACE -l app=seckill-redis -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
if [ -z "$REDIS_POD" ]; then
    echo "❌ 未找到 Redis Pod"
    exit 1
fi
echo "    Redis Pod: $REDIS_POD"

# 3. 检查是否已有数据
echo ""
echo ">>> 检查现有数据..."
EXISTING=$(kubectl exec -n $NAMESPACE $POSTGRES_POD -- psql -U postgres -d seckill -c "SELECT COUNT(*) FROM seckill;" -t 2>/dev/null | tr -d ' ')
echo "    现有秒杀活动数：$EXISTING"

# 4. 插入测试数据
echo ""
echo ">>> 插入数据库测试数据..."

kubectl exec -n $NAMESPACE $POSTGRES_POD -- psql -U postgres -d seckill << 'EOF'
-- 插入秒杀活动（当前时间前后 1 小时，确保活动"正在进行"）
INSERT INTO seckill (id, product_name, stock, start_time, end_time, version, created_at, updated_at)
VALUES (1, 'iPhone 15 Pro Max', 100, NOW() - INTERVAL '1 hour', NOW() + INTERVAL '24 hour', 0, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
    stock = 100,
    start_time = NOW() - INTERVAL '1 hour',
    end_time = NOW() + INTERVAL '24 hour',
    updated_at = NOW();

-- 插入库存记录
INSERT INTO inventory (seckill_id, total_stock, available_stock, locked_stock, sold_stock, version)
VALUES (1, 100, 100, 0, 0, 0)
ON CONFLICT (seckill_id) DO UPDATE SET
    total_stock = 100,
    available_stock = 100,
    locked_stock = 0,
    sold_stock = 0,
    version = 0;

-- 清理旧的订单和结果数据
TRUNCATE seckill_order, seckill_result RESTART IDENTITY CASCADE;

-- 显示数据
SELECT '=== 秒杀活动 ===' as info;
SELECT id, product_name, stock, start_time, end_time FROM seckill WHERE id = 1;

SELECT '=== 库存记录 ===' as info;
SELECT seckill_id, total_stock, available_stock, locked_stock, sold_stock FROM inventory WHERE seckill_id = 1;

SELECT '=== 初始化完成 ===' as info;
EOF

# 5. 设置 Redis 库存
echo ""
echo ">>> 设置 Redis 库存..."
kubectl exec -n $NAMESPACE $REDIS_POD -- redis-cli SET "seckill:stock:1" "100"
REDIS_VALUE=$(kubectl exec -n $NAMESPACE $REDIS_POD -- redis-cli GET "seckill:stock:1")
echo "    Redis 库存：$REDIS_VALUE"

echo ""
echo "============================================"
echo "  初始化完成！"
echo "============================================"
echo ""
echo "现在可以运行测试脚本:"
echo "  ./k8s/test-seckill.sh"
echo "  ./k8s/stress-test.sh"
echo ""
