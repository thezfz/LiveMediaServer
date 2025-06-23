#!/bin/bash

# Live Media Server 端到端测试脚本
# 测试完整的流媒体工作流程

set -e

echo "🧪 Live Media Server 端到端测试"
echo "=================================="

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 测试函数
test_service() {
    local service_name="$1"
    local url="$2"
    local expected_status="$3"
    
    echo -n "🔍 测试 $service_name... "
    
    if response=$(curl -s -w "%{http_code}" "$url" -o /tmp/response.txt); then
        http_code="${response: -3}"
        if [ "$http_code" = "$expected_status" ]; then
            echo -e "${GREEN}✅ 通过${NC}"
            return 0
        else
            echo -e "${RED}❌ 失败 (HTTP $http_code)${NC}"
            return 1
        fi
    else
        echo -e "${RED}❌ 连接失败${NC}"
        return 1
    fi
}

# 等待服务启动
wait_for_service() {
    local service_name="$1"
    local url="$2"
    local max_attempts=30
    local attempt=1
    
    echo "⏳ 等待 $service_name 启动..."
    
    while [ $attempt -le $max_attempts ]; do
        if curl -s "$url" > /dev/null 2>&1; then
            echo -e "${GREEN}✅ $service_name 已启动${NC}"
            return 0
        fi
        echo "   尝试 $attempt/$max_attempts..."
        sleep 2
        ((attempt++))
    done
    
    echo -e "${RED}❌ $service_name 启动超时${NC}"
    return 1
}

echo ""
echo "📋 第一阶段：服务健康检查"
echo "========================"

# 等待所有服务启动
wait_for_service "Web API 服务器" "http://localhost:8080/api/actuator/health"
wait_for_service "转码服务" "http://localhost:8081/health"

# 测试各个服务
echo ""
echo "🔍 测试服务端点..."

test_service "Web API 健康检查" "http://localhost:8080/api/actuator/health" "200"
test_service "转码服务健康检查" "http://localhost:8081/health" "200"

# 测试RTMP端口
echo -n "🔍 测试 RTMP 服务器... "
if nc -z localhost 1935; then
    echo -e "${GREEN}✅ 通过${NC}"
else
    echo -e "${RED}❌ 失败${NC}"
    exit 1
fi

echo ""
echo "📋 第二阶段：API 功能测试"
echo "========================"

# 测试流管理API
echo "🔍 测试流管理 API..."

# 获取所有流
echo -n "   获取流列表... "
if curl -s "http://localhost:8080/api/streams" > /tmp/streams.json; then
    echo -e "${GREEN}✅ 通过${NC}"
else
    echo -e "${RED}❌ 失败${NC}"
    exit 1
fi

# 创建测试流
echo -n "   创建测试流... "
stream_data='{
    "streamKey": "test_stream_'$(date +%s)'",
    "clientIp": "127.0.0.1",
    "timestamp": "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'",
    "action": "start"
}'

if response=$(curl -s -w "%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -d "$stream_data" \
    "http://localhost:8080/api/streams/start" \
    -o /tmp/create_stream.json); then
    
    http_code="${response: -3}"
    if [ "$http_code" = "200" ] || [ "$http_code" = "201" ]; then
        echo -e "${GREEN}✅ 通过${NC}"
        # 提取流ID用于后续测试
        if command -v jq > /dev/null; then
            STREAM_ID=$(jq -r '.id // .streamId // empty' /tmp/create_stream.json 2>/dev/null || echo "")
        fi
    else
        echo -e "${RED}❌ 失败 (HTTP $http_code)${NC}"
        cat /tmp/create_stream.json
    fi
else
    echo -e "${RED}❌ 连接失败${NC}"
    exit 1
fi

echo ""
echo "📋 第三阶段：转码服务测试"
echo "========================"

# 测试转码服务API
echo -n "🔍 测试转码服务状态... "
if curl -s "http://localhost:8081/health" | grep -q "UP"; then
    echo -e "${GREEN}✅ 通过${NC}"
else
    echo -e "${RED}❌ 失败${NC}"
fi

echo ""
echo "📋 第四阶段：容器状态检查"
echo "========================"

echo "🔍 检查容器状态..."
podman ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

echo ""
echo "📋 测试总结"
echo "==========="

echo -e "${BLUE}🎯 核心服务状态:${NC}"
echo "   • Web API 服务器: http://localhost:8080/api"
echo "   • 转码服务: http://localhost:8081"
echo "   • RTMP 服务器: rtmp://localhost:1935"

echo ""
echo -e "${BLUE}🔗 有用的端点:${NC}"
echo "   • 健康检查: http://localhost:8080/api/actuator/health"
echo "   • 流管理: http://localhost:8080/api/streams"
echo "   • H2 数据库控制台: http://localhost:8080/api/h2-console"
echo "   • 转码服务状态: http://localhost:8081/health"

echo ""
echo -e "${GREEN}🎉 Live Media Server 测试完成！${NC}"

# 清理临时文件
rm -f /tmp/response.txt /tmp/streams.json /tmp/create_stream.json
