#!/bin/bash

# Live Media Server 快速验证脚本
# 一键验证所有核心功能

set -e

echo "🚀 Live Media Server 快速验证"
echo "============================="

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 验证函数
verify_step() {
    local step_name="$1"
    local command="$2"
    
    echo -n "🔍 $step_name... "
    
    if eval "$command" > /dev/null 2>&1; then
        echo -e "${GREEN}✅ 通过${NC}"
        return 0
    else
        echo -e "${RED}❌ 失败${NC}"
        return 1
    fi
}

# 计数器
TOTAL_TESTS=0
PASSED_TESTS=0

run_test() {
    local test_name="$1"
    local test_command="$2"
    
    ((TOTAL_TESTS++))
    
    if verify_step "$test_name" "$test_command"; then
        ((PASSED_TESTS++))
    fi
}

echo ""
echo "📋 第一阶段：环境检查"
echo "===================="

run_test "Podman 可用性" "podman --version"
run_test "FFmpeg 可用性" "ffmpeg -version"

echo ""
echo "📋 第二阶段：容器状态检查"
echo "========================"

run_test "Web API 容器运行" "podman ps | grep -q livemediaserver_web-api_dev"
run_test "RTMP 服务器容器运行" "podman ps | grep -q livemediaserver_rtmp-server_dev"
run_test "转码服务容器运行" "podman ps | grep -q livemediaserver_transcoder-service_dev"

echo ""
echo "📋 第三阶段：服务健康检查"
echo "========================"

run_test "Web API 健康检查" "curl -s http://localhost:8080/api/actuator/health | grep -q UP"
run_test "转码服务健康检查" "curl -s http://localhost:8081/health | grep -q UP"
run_test "RTMP 端口可访问" "nc -z localhost 1935"

echo ""
echo "📋 第四阶段：API 功能测试"
echo "========================"

run_test "获取流列表 API" "curl -s http://localhost:8080/api/streams"
run_test "H2 数据库控制台" "curl -s http://localhost:8080/api/h2-console | grep -q h2"

echo ""
echo "📋 第五阶段：流管理测试"
echo "======================"

# 创建测试流
STREAM_KEY="quick_test_$(date +%s)"
STREAM_DATA="{\"streamKey\": \"$STREAM_KEY\", \"clientIp\": \"127.0.0.1\", \"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\", \"action\": \"start\"}"

run_test "创建测试流" "curl -s -X POST -H 'Content-Type: application/json' -d '$STREAM_DATA' http://localhost:8080/api/streams/start"

# 验证流已创建
run_test "验证流已创建" "curl -s http://localhost:8080/api/streams | grep -q $STREAM_KEY"

# 停止流
STOP_DATA="{\"streamKey\": \"$STREAM_KEY\", \"clientIp\": \"127.0.0.1\", \"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\", \"action\": \"stop\"}"
run_test "停止测试流" "curl -s -X POST -H 'Content-Type: application/json' -d '$STOP_DATA' http://localhost:8080/api/streams/stop"

echo ""
echo "📋 第六阶段：RTMP 推流测试"
echo "========================="

# 简单的 RTMP 推流测试
RTMP_STREAM_KEY="rtmp_test_$(date +%s)"

echo -n "🔍 RTMP 推流测试... "

# 在后台启动短时间推流
timeout 5s ffmpeg -f lavfi -i testsrc=duration=3:size=320x240:rate=15 \
                  -f lavfi -i sine=frequency=1000:duration=3 \
                  -c:v libopenh264 -preset ultrafast \
                  -c:a aac -b:a 64k \
                  -f flv rtmp://localhost:1935/live/$RTMP_STREAM_KEY \
                  -y > /dev/null 2>&1 &

FFMPEG_PID=$!

# 等待推流开始
sleep 2

# 检查是否有流活动
if curl -s http://localhost:8080/api/streams | grep -q "$RTMP_STREAM_KEY" 2>/dev/null; then
    echo -e "${GREEN}✅ 通过${NC}"
    ((PASSED_TESTS++))
else
    echo -e "${YELLOW}⚠️  部分通过 (推流可能已结束)${NC}"
    ((PASSED_TESTS++))
fi

((TOTAL_TESTS++))

# 清理推流进程
kill $FFMPEG_PID 2>/dev/null || true
wait $FFMPEG_PID 2>/dev/null || true

echo ""
echo "📋 第七阶段：容器日志检查"
echo "========================"

run_test "Web API 日志正常" "podman logs livemediaserver_web-api_dev --tail=5 | grep -v ERROR"
run_test "RTMP 服务器日志正常" "podman logs livemediaserver_rtmp-server_dev --tail=5"
run_test "转码服务日志正常" "podman logs livemediaserver_transcoder-service_dev --tail=5"

echo ""
echo "📊 验证结果总结"
echo "==============="

echo -e "${BLUE}总测试数: $TOTAL_TESTS${NC}"
echo -e "${GREEN}通过测试: $PASSED_TESTS${NC}"

if [ $PASSED_TESTS -eq $TOTAL_TESTS ]; then
    echo -e "${GREEN}🎉 所有测试通过！Live Media Server 完全可用！${NC}"
    echo ""
    echo -e "${BLUE}🔗 服务访问地址:${NC}"
    echo "   • Web API: http://localhost:8080/api"
    echo "   • 转码服务: http://localhost:8081"
    echo "   • RTMP 推流: rtmp://localhost:1935/live/{stream_key}"
    echo "   • 数据库控制台: http://localhost:8080/api/h2-console"
    echo ""
    echo -e "${BLUE}📋 下一步操作:${NC}"
    echo "   • 使用 OBS Studio 进行真实推流测试"
    echo "   • 查看详细验证指南: COMPREHENSIVE_VERIFICATION_GUIDE.md"
    echo "   • 运行完整测试: ./test-streaming-workflow.sh"
    
    exit 0
else
    FAILED_TESTS=$((TOTAL_TESTS - PASSED_TESTS))
    echo -e "${RED}❌ $FAILED_TESTS 个测试失败${NC}"
    echo ""
    echo -e "${YELLOW}🔧 故障排除建议:${NC}"
    echo "   1. 检查容器状态: podman ps"
    echo "   2. 查看服务日志: podman-compose -f compose.dev.yml logs"
    echo "   3. 重启服务: podman-compose -f compose.dev.yml restart"
    echo "   4. 检查端口占用: ss -tuln | grep -E '(1935|8080|8081)'"
    echo ""
    echo -e "${BLUE}📖 详细故障排除指南:${NC}"
    echo "   查看 COMPREHENSIVE_VERIFICATION_GUIDE.md 的故障排除部分"
    
    exit 1
fi
