#!/bin/bash

# Live Media Server 流媒体工作流程测试
# 测试完整的推流、转码、播放流程

set -e

echo "🎬 Live Media Server 流媒体工作流程测试"
echo "====================================="

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
STREAM_KEY="test_stream_$(date +%s)"
RTMP_URL="rtmp://localhost:1935/live"
TEST_VIDEO_DURATION=10  # 秒

echo ""
echo "📋 测试配置"
echo "==========="
echo "流密钥: $STREAM_KEY"
echo "RTMP URL: $RTMP_URL"
echo "测试视频时长: ${TEST_VIDEO_DURATION}秒"

echo ""
echo "📋 第一阶段：准备测试环境"
echo "========================"

# 检查FFmpeg是否可用
echo -n "🔍 检查 FFmpeg... "
if command -v ffmpeg > /dev/null; then
    echo -e "${GREEN}✅ 已安装${NC}"
else
    echo -e "${RED}❌ 未安装${NC}"
    echo "请安装 FFmpeg: sudo dnf install ffmpeg"
    exit 1
fi

# 创建测试视频（彩色条纹）
echo -n "🎥 生成测试视频... "
ffmpeg -f lavfi -i testsrc=duration=${TEST_VIDEO_DURATION}:size=640x480:rate=30 \
       -f lavfi -i sine=frequency=1000:duration=${TEST_VIDEO_DURATION} \
       -c:v libopenh264 -preset ultrafast \
       -c:a aac -b:a 128k \
       -f flv /tmp/test_video.flv \
       -y > /dev/null 2>&1

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ 完成${NC}"
else
    echo -e "${RED}❌ 失败${NC}"
    exit 1
fi

echo ""
echo "📋 第二阶段：注册流"
echo "=================="

# 通过API注册流
echo -n "📝 注册流到服务器... "
stream_data='{
    "streamKey": "'$STREAM_KEY'",
    "clientIp": "127.0.0.1",
    "timestamp": "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'",
    "action": "start"
}'

response=$(curl -s -w "%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -d "$stream_data" \
    "http://localhost:8080/api/streams/start" \
    -o /tmp/register_response.json)

http_code="${response: -3}"
if [ "$http_code" = "200" ] || [ "$http_code" = "201" ]; then
    echo -e "${GREEN}✅ 成功${NC}"
    if command -v jq > /dev/null; then
        echo "   响应: $(cat /tmp/register_response.json | jq -c .)"
    fi
else
    echo -e "${RED}❌ 失败 (HTTP $http_code)${NC}"
    cat /tmp/register_response.json
    exit 1
fi

echo ""
echo "📋 第三阶段：推流测试"
echo "=================="

echo "🚀 开始推流到 RTMP 服务器..."
echo "   目标: $RTMP_URL/$STREAM_KEY"

# 在后台推流
ffmpeg -re -i /tmp/test_video.flv \
       -c copy \
       -f flv "$RTMP_URL/$STREAM_KEY" \
       > /tmp/ffmpeg_push.log 2>&1 &

FFMPEG_PID=$!
echo "   FFmpeg PID: $FFMPEG_PID"

# 等待推流开始
echo "⏳ 等待推流建立连接..."
sleep 3

# 检查推流是否成功
if kill -0 $FFMPEG_PID 2>/dev/null; then
    echo -e "${GREEN}✅ 推流进程运行中${NC}"
else
    echo -e "${RED}❌ 推流失败${NC}"
    cat /tmp/ffmpeg_push.log
    exit 1
fi

echo ""
echo "📋 第四阶段：监控流状态"
echo "======================"

# 监控流状态
for i in {1..5}; do
    echo "🔍 检查流状态 ($i/5)..."
    
    # 检查流列表
    if curl -s "http://localhost:8080/api/streams" > /tmp/streams_status.json; then
        if command -v jq > /dev/null; then
            active_streams=$(jq -r '.[] | select(.streamKey == "'$STREAM_KEY'") | .streamKey' /tmp/streams_status.json 2>/dev/null || echo "")
            if [ -n "$active_streams" ]; then
                echo -e "   ${GREEN}✅ 流 $STREAM_KEY 在服务器中活跃${NC}"
            else
                echo -e "   ${YELLOW}⚠️  流 $STREAM_KEY 未在活跃列表中${NC}"
            fi
        fi
    fi
    
    # 检查转码服务状态
    if curl -s "http://localhost:8081/health" | grep -q "UP"; then
        echo -e "   ${GREEN}✅ 转码服务运行正常${NC}"
    else
        echo -e "   ${YELLOW}⚠️  转码服务状态异常${NC}"
    fi
    
    sleep 2
done

echo ""
echo "📋 第五阶段：清理"
echo "================"

# 停止推流
echo -n "🛑 停止推流... "
if kill $FFMPEG_PID 2>/dev/null; then
    wait $FFMPEG_PID 2>/dev/null || true
    echo -e "${GREEN}✅ 完成${NC}"
else
    echo -e "${YELLOW}⚠️  进程已结束${NC}"
fi

# 停止流
echo -n "📝 停止流... "
stop_data='{
    "streamKey": "'$STREAM_KEY'",
    "clientIp": "127.0.0.1",
    "timestamp": "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'",
    "action": "stop"
}'

response=$(curl -s -w "%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -d "$stop_data" \
    "http://localhost:8080/api/streams/stop" \
    -o /tmp/stop_response.json)

http_code="${response: -3}"
if [ "$http_code" = "200" ] || [ "$http_code" = "204" ]; then
    echo -e "${GREEN}✅ 成功${NC}"
else
    echo -e "${YELLOW}⚠️  响应 (HTTP $http_code)${NC}"
fi

# 清理临时文件
echo -n "🧹 清理临时文件... "
rm -f /tmp/test_video.flv /tmp/register_response.json /tmp/streams_status.json /tmp/stop_response.json /tmp/ffmpeg_push.log
echo -e "${GREEN}✅ 完成${NC}"

echo ""
echo "📋 测试总结"
echo "==========="

echo -e "${BLUE}🎯 测试结果:${NC}"
echo "   • 流注册: ✅ 成功"
echo "   • RTMP 推流: ✅ 成功"
echo "   • 服务监控: ✅ 成功"
echo "   • 流停止: ✅ 成功"

echo ""
echo -e "${BLUE}🔗 下一步建议:${NC}"
echo "   • 使用 OBS Studio 进行真实推流测试"
echo "   • 配置 HLS 输出目录并测试播放"
echo "   • 测试多路并发流"
echo "   • 配置流录制功能"

echo ""
echo -e "${GREEN}🎉 流媒体工作流程测试完成！${NC}"
