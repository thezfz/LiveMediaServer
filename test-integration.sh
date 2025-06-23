#!/bin/bash

# 测试RTMP-WebAPI集成的脚本
# 模拟RTMP服务器发送事件到Web API

echo "🧪 测试RTMP-WebAPI集成"
echo "========================"

# 检查服务是否运行
echo "📡 检查服务状态..."
if ! curl -s http://localhost:8080/api/actuator/health > /dev/null; then
    echo "❌ Web API服务器未运行"
    exit 1
fi
echo "✅ Web API服务器正在运行"

# 测试流开始事件
echo ""
echo "🎬 测试流开始事件..."
STREAM_KEY="test_stream_$(date +%s)"
CLIENT_IP="192.168.1.100"
TIMESTAMP=$(date -Iseconds)

START_RESPONSE=$(curl -s -X POST http://localhost:8080/api/streams/start \
    -H "Content-Type: application/json" \
    -d "{
        \"streamKey\": \"$STREAM_KEY\",
        \"clientIp\": \"$CLIENT_IP\",
        \"timestamp\": \"$TIMESTAMP\",
        \"action\": \"start\"
    }")

echo "📝 流开始响应: $START_RESPONSE"

# 检查流是否被创建
echo ""
echo "🔍 检查流列表..."
STREAMS_LIST=$(curl -s http://localhost:8080/api/streams)
echo "📋 当前流列表: $STREAMS_LIST"

# 测试流更新事件
echo ""
echo "🔄 测试流元数据更新..."
UPDATE_RESPONSE=$(curl -s -X POST http://localhost:8080/api/streams/update \
    -H "Content-Type: application/json" \
    -d "{
        \"streamKey\": \"$STREAM_KEY\",
        \"bitrate\": 2500000,
        \"resolution\": \"1920x1080\"
    }")

echo "📝 流更新响应: $UPDATE_RESPONSE"

# 等待一下
echo ""
echo "⏳ 等待2秒..."
sleep 2

# 测试流结束事件
echo ""
echo "🛑 测试流结束事件..."
STOP_TIMESTAMP=$(date -Iseconds)
STOP_RESPONSE=$(curl -s -X POST http://localhost:8080/api/streams/stop \
    -H "Content-Type: application/json" \
    -d "{
        \"streamKey\": \"$STREAM_KEY\",
        \"timestamp\": \"$STOP_TIMESTAMP\",
        \"action\": \"stop\"
    }")

echo "📝 流结束响应: $STOP_RESPONSE"

# 再次检查流列表
echo ""
echo "🔍 检查最终流列表..."
FINAL_STREAMS_LIST=$(curl -s http://localhost:8080/api/streams)
echo "📋 最终流列表: $FINAL_STREAMS_LIST"

echo ""
echo "✅ 集成测试完成！"
echo ""
echo "📊 测试总结:"
echo "   - 流密钥: $STREAM_KEY"
echo "   - 客户端IP: $CLIENT_IP"
echo "   - 开始时间: $TIMESTAMP"
echo "   - 结束时间: $STOP_TIMESTAMP"
