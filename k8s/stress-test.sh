#!/bin/bash
# 秒杀系统压力测试脚本
# 模拟高并发场景测试 K8s 部署的秒杀系统

set -e

# ========== 配置区 ==========
BASE_URL="${BASE_URL:-http://localhost:30080}"
SECKILL_ID="${SECKILL_ID:-1}"

# 测试场景配置
TOTAL_REQUESTS="${TOTAL_REQUESTS:-200}"     # 总请求数
CONCURRENT_LEVEL="${CONCURRENT_LEVEL:-20}"  # 并发度（每批多少个请求）
SLEEP_INTERVAL="${SLEEP_INTERVAL:-0.01}"    # 请求间隔（秒）

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo ""
echo "========================================================"
echo "  秒杀系统压力测试脚本"
echo "========================================================"
echo ""
echo -e "${YELLOW}测试配置:${NC}"
echo "  - 服务地址：$BASE_URL"
echo "  - 秒杀活动 ID: $SECKILL_ID"
echo "  - 总请求数：$TOTAL_REQUESTS"
echo "  - 并发度：$CONCURRENT_LEVEL"
echo "  - 请求间隔：${SLEEP_INTERVAL}s"
echo ""

# ========== 函数定义 ==========

# 检查服务可用性
check_service() {
    echo -e "${YELLOW}>>> 检查服务可用性...${NC}"
    if curl -s -f "$BASE_URL/actuator/health" | grep -q '"status":"UP"'; then
        echo -e "${GREEN}    ✅ 服务可用${NC}"
        return 0
    else
        echo -e "${RED}    ❌ 服务不可用${NC}"
        return 1
    fi
}

# 获取初始库存
get_initial_stock() {
    curl -s "$BASE_URL/api/seckill/$SECKILL_ID" | grep -o '"stock":[0-9]*' | cut -d: -f2 || echo "0"
}

# 并发测试函数
run_stress_test() {
    local total=$1
    local concurrent=$2

    # 创建临时文件
    TMP_DIR=$(mktemp -d)
    RESULTS_FILE="$TMP_DIR/results"

    echo -e "${YELLOW}>>> 开始压力测试...${NC}"
    echo "    发送 $total 个请求，并发度 $concurrent"
    echo ""

    # 记录开始时间
    START_TIME=$(date +%s.%N)

    # 计数器
    success=0
    fail=0
    processing=0

    # 发起请求
    for i in $(seq 1 $total); do
        USER_ID=$((5000 + i))

        # 异步发起请求
        (
            RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
                "$BASE_URL/api/seckill/$SECKILL_ID?userId=$USER_ID" \
                2>/dev/null)
            HTTP_CODE=$(echo "$RESPONSE" | tail -1)
            BODY=$(echo "$RESPONSE" | head -n -1)

            # 判断结果
            if [ "$HTTP_CODE" = "200" ]; then
                if echo "$BODY" | grep -q '"success":true'; then
                    echo "SUCCESS" >> "$RESULTS_FILE"
                elif echo "$BODY" | grep -q '"success":false"message":"处理中"\|"处理中"'; then
                    echo "PROCESSING" >> "$RESULTS_FILE"
                else
                    echo "FAIL" >> "$RESULTS_FILE"
                fi
            else
                echo "FAIL" >> "$RESULTS_FILE"
            fi
        ) &

        # 控制并发
        if [ $((i % concurrent)) -eq 0 ]; then
            wait
            # 显示进度
            current=$(wc -l < "$RESULTS_FILE" 2>/dev/null || echo "0")
            echo -ne "\r    进度：$current/$total"
        fi

        # 小延迟模拟真实场景
        sleep $SLEEP_INTERVAL
    done

    # 等待剩余请求
    wait

    # 记录结束时间
    END_TIME=$(date +%s.%N)

    # 计算统计
    echo ""
    success=$(grep -c "SUCCESS" "$RESULTS_FILE" 2>/dev/null || echo "0")
    processing=$(grep -c "PROCESSING" "$RESULTS_FILE" 2>/dev/null || echo "0")
    fail=$(grep -c "FAIL" "$RESULTS_FILE" 2>/dev/null || echo "0")

    # 计算耗时
    ELAPSED=$(echo "$END_TIME - $START_TIME" | bc)
    QPS=$(echo "scale=2; $total / $ELAPSED" | bc 2>/dev/null || echo "N/A")

    echo ""
    echo -e "${YELLOW}>>> 测试结果:${NC}"
    echo -e "    - 总请求数：${YELLOW}$total${NC}"
    echo -e "    - 成功：${GREEN}$success${NC}"
    echo -e "    - 处理中：${YELLOW}$processing${NC}"
    echo -e "    - 失败：${RED}$fail${NC}"
    echo -e "    - 耗时：${ELAPSED}s"
    echo -e "    - QPS: ${QPS}"

    # 清理
    rm -rf "$TMP_DIR"
}

# 查询秒杀结果
check_results() {
    echo ""
    echo -e "${YELLOW}>>> 查询部分用户秒杀结果 (5 秒后轮询)...${NC}"
    sleep 5

    for i in 1 10 20 30 40 50; do
        USER_ID=$((5000 + i))
        RESULT=$(curl -s "$BASE_URL/api/seckill/$SECKILL_ID/result?userId=$USER_ID" 2>/dev/null)
        if [ -n "$RESULT" ] && [ "$RESULT" != "null" ]; then
            STATUS=$(echo "$RESULT" | grep -o '"status":"[^"]*"' | cut -d'"' -f4 || echo "N/A")
            echo "    用户 $USER_ID: $STATUS"
        fi
    done
}

# ========== 主流程 ==========

# 1. 检查服务
if ! check_service; then
    exit 1
fi

# 2. 获取初始状态
echo ""
echo -e "${YELLOW}>>> 获取初始状态...${NC}"
INITIAL_STOCK=$(get_initial_stock)
echo "    初始库存：$INITIAL_STOCK"

# 3. 运行压力测试
run_stress_test $TOTAL_REQUESTS $CONCURRENT_LEVEL

# 4. 查询结果
check_results

# 5. 获取最终状态
echo ""
echo -e "${YELLOW}>>> 获取最终状态...${NC}"
FINAL_STOCK=$(curl -s "$BASE_URL/api/seckill/$SECKILL_ID" | grep -o '"stock":[0-9]*' | cut -d: -f2 || echo "N/A")
echo "    剩余库存：$FINAL_STOCK"

# 6. 验证
echo ""
echo -e "${YELLOW}>>> 验证结果...${NC}"
if [ "$INITIAL_STOCK" != "N/A" ] && [ "$FINAL_STOCK" != "N/A" ]; then
    SOLD=$((INITIAL_STOCK - FINAL_STOCK))
    echo "    售出数量：$SOLD"
    if [ $SOLD -le $INITIAL_STOCK ]; then
        echo -e "${GREEN}    ✅ 无超卖现象${NC}"
    else
        echo -e "${RED}    ❌ 发生超卖！${NC}"
    fi
else
    echo "    无法验证（库存数据不可用）"
fi

echo ""
echo "========================================================"
echo -e "${GREEN}  测试完成！${NC}"
echo "========================================================"
echo ""
