#!/bin/bash
# 秒杀系统本地测试脚本
# 用于测试部署在 K8s 中的秒杀系统

set -e

# 配置
BASE_URL="${BASE_URL:-http://localhost:30080}"
SECKILL_ID="${SECKILL_ID:-1}"
CONCURRENT_USERS="${CONCURRENT_USERS:-50}"
STOCK_COUNT="${STOCK_COUNT:-100}"

echo "============================================"
echo "  秒杀系统测试脚本"
echo "============================================"
echo ""
echo "配置:"
echo "  - 服务地址：$BASE_URL"
echo "  - 秒杀活动 ID: $SECKILL_ID"
echo "  - 并发用户数：$CONCURRENT_USERS"
echo "  - 库存数量：$STOCK_COUNT"
echo ""

# 1. 检查服务是否可用
echo ">>> 1. 检查服务健康状态..."
HEALTH=$(curl -s "$BASE_URL/actuator/health" 2>/dev/null || echo '{"status":"DOWN"}')
if echo "$HEALTH" | grep -q '"status":"UP"'; then
    echo "    ✅ 服务健康检查通过"
else
    echo "    ❌ 服务不可用：$HEALTH"
    exit 1
fi

# 2. 查询秒杀活动列表
echo ""
echo ">>> 2. 查询秒杀活动列表..."
SECKILLS=$(curl -s "$BASE_URL/api/seckill" 2>/dev/null)
echo "    响应：$SECKILLS"

# 3. 创建测试数据（如果有初始化接口）
echo ""
echo ">>> 3. 检查秒杀活动详情..."
DETAIL=$(curl -s "$BASE_URL/api/seckill/$SECKILL_ID" 2>/dev/null)
echo "    响应：$DETAIL"

# 4. 并发秒杀测试
echo ""
echo ">>> 4. 开始并发秒杀测试..."
echo "    发送 $CONCURRENT_USERS 个并发请求..."

# 创建临时目录
TMP_DIR=$(mktemp -d)
SUCCESS_FILE="$TMP_DIR/success"
FAIL_FILE="$TMP_DIR/fail"
touch "$SUCCESS_FILE" "$FAIL_FILE"

# 记录开始时间
START_TIME=$(date +%s%N)

# 发起并发请求
for i in $(seq 1 $CONCURRENT_USERS); do
    USER_ID=$((1000 + i))
    (
        RESPONSE=$(curl -s -X POST "$BASE_URL/api/seckill/$SECKILL_ID?userId=$USER_ID" 2>/dev/null)
        if echo "$RESPONSE" | grep -q '"success":true\|"处理中"'; then
            echo "$USER_ID: $RESPONSE" >> "$SUCCESS_FILE"
        else
            echo "$USER_ID: $RESPONSE" >> "$FAIL_FILE"
        fi
    ) &

    # 控制并发度，每批 20 个
    if [ $((i % 20)) -eq 0 ]; then
        wait
    fi
done

# 等待所有请求完成
wait

# 记录结束时间
END_TIME=$(date +%s%N)

# 计算耗时（毫秒）
ELAPSED_MS=$(( (END_TIME - START_TIME) / 1000000 ))

# 统计结果
SUCCESS_COUNT=$(wc -l < "$SUCCESS_FILE" | tr -d ' ')
FAIL_COUNT=$(wc -l < "$FAIL_FILE" | tr -d ' ')

echo ""
echo ">>> 5. 测试结果统计:"
echo "    - 总请求数：$CONCURRENT_USERS"
echo "    - 成功请求数：$SUCCESS_COUNT"
echo "    - 失败请求数：$FAIL_COUNT"
echo "    - 总耗时：${ELAPSED_MS}ms"
echo "    - QPS: $(( CONCURRENT_USERS * 1000 / ELAPSED_MS ))"

# 6. 查询秒杀结果
echo ""
echo ">>> 6. 查询部分用户的秒杀结果..."
for i in 1 5 10 15 20; do
    USER_ID=$((1000 + i))
    RESULT=$(curl -s "$BASE_URL/api/seckill/$SECKILL_ID/result?userId=$USER_ID" 2>/dev/null)
    echo "    用户 $USER_ID: $RESULT"
done

# 7. 查询最终库存
echo ""
echo ">>> 7. 查询秒杀活动最终状态..."
FINAL_STATUS=$(curl -s "$BASE_URL/api/seckill/$SECKILL_ID" 2>/dev/null)
echo "    响应：$FINAL_STATUS"

# 清理临时文件
rm -rf "$TMP_DIR"

echo ""
echo "============================================"
echo "  测试完成！"
echo "============================================"
echo ""

# 8. 可选：查询订单数
echo ">>> 8. 查询订单统计..."
ORDER_COUNT=$(kubectl get pods -n seckill -l app=seckill-app -o json 2>/dev/null | jq '.items | length' 2>/dev/null || echo "N/A")
echo "    - 应用 Pod 数：$ORDER_COUNT"
echo ""

# 显示成功/失败样例
if [ -s "$SUCCESS_FILE" ]; then
    echo ">>> 成功请求样例:"
    head -3 "$SUCCESS_FILE" | while read line; do echo "    $line"; done
fi

if [ -s "$FAIL_FILE" ]; then
    echo ""
    echo ">>> 失败请求样例:"
    head -3 "$FAIL_FILE" | while read line; do echo "    $line"; done
fi
