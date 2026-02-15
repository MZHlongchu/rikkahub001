#!/bin/bash
# 容器环境单元测试运行脚本

set -e

echo "=========================================="
echo "RikkaHub 容器环境单元测试"
echo "=========================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查是否在项目根目录
if [ ! -f "gradlew" ]; then
    echo -e "${RED}错误: 请在项目根目录运行此脚本${NC}"
    exit 1
fi

echo "步骤 1: 清理构建缓存..."
./gradlew clean --quiet

echo ""
echo "步骤 2: 运行单元测试 (JVM)..."
echo "=========================================="

# 运行 PRootManager 相关测试
./gradlew :app:testDebugUnitTest \
    --tests "me.rerere.rikkahub.data.container.PRootManagerTest" \
    --tests "me.rerere.rikkahub.data.container.PackageManagerTest" \
    --tests "me.rerere.rikkahub.data.ai.tools.LocalToolsContainerTest" \
    --info 2>&1 | tee test-output.log

TEST_RESULT=${PIPESTATUS[0]}

echo ""
echo "=========================================="

if [ $TEST_RESULT -eq 0 ]; then
    echo -e "${GREEN}✓ 单元测试全部通过！${NC}"
    echo ""
    echo "测试统计:"
    grep -E "(tests? completed|PASSED|FAILED)" test-output.log || echo "请查看 test-output.log 了解详情"
else
    echo -e "${RED}✗ 单元测试失败${NC}"
    echo ""
    echo "失败详情:"
    grep -A 5 "FAILED\|AssertionError" test-output.log | head -50 || echo "请查看 test-output.log 了解详情"
fi

echo ""
echo "=========================================="
echo "报告文件:"
echo "  - 控制台输出: test-output.log"
echo "  - HTML 报告: app/build/reports/tests/testDebugUnitTest/index.html"
echo "=========================================="

exit $TEST_RESULT
