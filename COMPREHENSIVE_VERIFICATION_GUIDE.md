# 🧪 Live Media Server 全面功能验证指南

## 📋 验证清单概览

本指南将帮助你全面验证 Live Media Server 的所有功能，确保系统正常运行。

### ✅ 验证项目
- [ ] 1. 环境准备和服务启动
- [ ] 2. 基础服务健康检查
- [ ] 3. Web API 功能测试
- [ ] 4. RTMP 服务器测试
- [ ] 5. 转码服务验证
- [ ] 6. 端到端流媒体工作流
- [ ] 7. 容器化部署验证
- [ ] 8. 性能和稳定性测试

---

## 🚀 第一阶段：环境准备和服务启动

### 1.1 检查系统环境
```bash
# 检查 Podman 版本
podman --version
# 预期输出：podman version 4.x.x

# 检查 Podman Compose
podman-compose --version
# 预期输出：podman-compose version 1.x.x

# 检查 FFmpeg (用于测试)
ffmpeg -version
# 预期输出：ffmpeg version 7.x.x
```

### 1.2 启动所有服务
```bash
# 进入项目目录
cd /home/thezfz/Advanced_Network_Programming/LiveMediaServer

# 后台启动所有服务
podman-compose -f compose.dev.yml up -d

# 验证容器状态
podman ps
# 预期看到 3 个运行中的容器：
# - livemediaserver_web-api_dev
# - livemediaserver_rtmp-server_dev  
# - livemediaserver_transcoder-service_dev
```

### 1.3 等待服务完全启动
```bash
# 等待服务启动 (约30秒)
sleep 30

# 检查服务日志
podman-compose -f compose.dev.yml logs --tail=20
```

---

## 🔍 第二阶段：基础服务健康检查

### 2.1 自动化健康检查
```bash
# 运行基础服务测试脚本
./test-live-media-server.sh

# 预期输出：
# 🧪 Live Media Server 端到端测试
# ✅ Web API 健康检查... 通过
# ✅ 转码服务健康检查... 通过  
# ✅ RTMP 服务器... 通过
# 🎉 Live Media Server 测试完成！
```

### 2.2 手动健康检查
```bash
# 1. Web API 健康检查
curl -s http://localhost:8080/api/actuator/health
# 预期输出：{"status":"UP"}

# 2. 转码服务健康检查
curl -s http://localhost:8081/health
# 预期输出：{"status":"UP","timestamp":"..."}

# 3. RTMP 端口检查
nc -z localhost 1935 && echo "RTMP 端口可访问" || echo "RTMP 端口不可访问"
# 预期输出：RTMP 端口可访问
```

---

## 🌐 第三阶段：Web API 功能测试

### 3.1 基础 API 端点测试
```bash
# 1. 获取所有流
curl -s http://localhost:8080/api/streams | jq .
# 预期输出：[] (空数组，因为还没有活跃流)

# 2. 健康检查端点
curl -s http://localhost:8080/api/actuator/health | jq .
# 预期输出：{"status":"UP"}

# 3. 数据库控制台访问 (浏览器)
echo "访问 http://localhost:8080/api/h2-console"
echo "JDBC URL: jdbc:h2:mem:testdb"
echo "用户名: sa"
echo "密码: (空)"
```

### 3.2 流管理 API 测试
```bash
# 创建测试流
STREAM_KEY="test_stream_$(date +%s)"
curl -X POST http://localhost:8080/api/streams/start \
  -H "Content-Type: application/json" \
  -d "{
    \"streamKey\": \"$STREAM_KEY\",
    \"clientIp\": \"127.0.0.1\",
    \"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",
    \"action\": \"start\"
  }" | jq .

# 验证流已创建
curl -s http://localhost:8080/api/streams | jq .

# 停止流
curl -X POST http://localhost:8080/api/streams/stop \
  -H "Content-Type: application/json" \
  -d "{
    \"streamKey\": \"$STREAM_KEY\",
    \"clientIp\": \"127.0.0.1\",
    \"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",
    \"action\": \"stop\"
  }" | jq .
```

---

## 📺 第四阶段：RTMP 服务器测试

### 4.1 RTMP 连接测试
```bash
# 使用 FFmpeg 测试 RTMP 连接
STREAM_KEY="test_rtmp_$(date +%s)"

# 生成测试视频并推流 (5秒)
ffmpeg -f lavfi -i testsrc=duration=5:size=640x480:rate=30 \
       -f lavfi -i sine=frequency=1000:duration=5 \
       -c:v libopenh264 -preset ultrafast \
       -c:a aac -b:a 128k \
       -f flv rtmp://localhost:1935/live/$STREAM_KEY \
       -y &

# 等待推流开始
sleep 2

# 检查流状态
curl -s http://localhost:8080/api/streams | jq .

# 等待推流完成
wait
```

### 4.2 OBS Studio 测试 (手动)
```bash
echo "=== OBS Studio 配置 ==="
echo "1. 打开 OBS Studio"
echo "2. 设置 -> 推流"
echo "3. 服务: 自定义"
echo "4. 服务器: rtmp://localhost:1935/live"
echo "5. 推流密钥: obs_test_stream"
echo "6. 点击 '开始推流'"
echo ""
echo "验证推流："
echo "curl http://localhost:8080/api/streams"
```

---

## 🔄 第五阶段：转码服务验证

### 5.1 转码服务状态检查
```bash
# 检查转码服务状态
curl -s http://localhost:8081/health | jq .

# 检查 FFmpeg 可用性
podman exec livemediaserver_transcoder-service_dev ffmpeg -version | head -1

# 检查 HLS 输出目录
podman exec livemediaserver_transcoder-service_dev ls -la /app/hls/
```

### 5.2 转码功能测试
```bash
# 检查转码服务日志
podman logs livemediaserver_transcoder-service_dev --tail=20

# 验证转码服务响应
curl -s http://localhost:8081/health
```

---

## 🎬 第六阶段：端到端流媒体工作流

### 6.1 自动化端到端测试
```bash
# 运行完整的流媒体工作流测试
./test-streaming-workflow.sh

# 预期输出：
# 🎬 Live Media Server 流媒体工作流程测试
# ✅ 流注册: 成功
# ✅ RTMP 推流: 成功  
# ✅ 服务监控: 成功
# ✅ 流停止: 成功
# 🎉 流媒体工作流程测试完成！
```

### 6.2 手动端到端验证
```bash
# 1. 注册流
STREAM_KEY="manual_test_$(date +%s)"
curl -X POST http://localhost:8080/api/streams/start \
  -H "Content-Type: application/json" \
  -d "{\"streamKey\": \"$STREAM_KEY\", \"clientIp\": \"127.0.0.1\"}"

# 2. 开始推流 (后台)
ffmpeg -f lavfi -i testsrc=duration=10:size=640x480:rate=30 \
       -f lavfi -i sine=frequency=1000:duration=10 \
       -c:v libopenh264 -preset ultrafast \
       -c:a aac -b:a 128k \
       -f flv rtmp://localhost:1935/live/$STREAM_KEY \
       -y > /tmp/ffmpeg.log 2>&1 &

FFMPEG_PID=$!

# 3. 监控流状态
for i in {1..5}; do
  echo "检查 $i/5..."
  curl -s http://localhost:8080/api/streams | jq .
  sleep 2
done

# 4. 停止推流
kill $FFMPEG_PID 2>/dev/null || true

# 5. 停止流
curl -X POST http://localhost:8080/api/streams/stop \
  -H "Content-Type: application/json" \
  -d "{\"streamKey\": \"$STREAM_KEY\", \"clientIp\": \"127.0.0.1\"}"
```

---

## 🐳 第七阶段：容器化部署验证

### 7.1 容器状态检查
```bash
# 检查所有容器状态
podman ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# 检查容器资源使用
podman stats --no-stream

# 检查网络连接
podman network ls
podman network inspect livemediaserver_default
```

### 7.2 容器日志分析
```bash
# 查看各服务日志
echo "=== Web API 日志 ==="
podman logs livemediaserver_web-api_dev --tail=10

echo "=== RTMP 服务器日志 ==="
podman logs livemediaserver_rtmp-server_dev --tail=10

echo "=== 转码服务日志 ==="
podman logs livemediaserver_transcoder-service_dev --tail=10
```

---

## 📊 第八阶段：性能和稳定性测试

### 8.1 并发连接测试
```bash
# 创建多个并发流 (谨慎使用)
for i in {1..3}; do
  STREAM_KEY="concurrent_test_$i"
  curl -X POST http://localhost:8080/api/streams/start \
    -H "Content-Type: application/json" \
    -d "{\"streamKey\": \"$STREAM_KEY\", \"clientIp\": \"127.0.0.1\"}" &
done
wait

# 检查所有流
curl -s http://localhost:8080/api/streams | jq .

# 清理测试流
for i in {1..3}; do
  STREAM_KEY="concurrent_test_$i"
  curl -X POST http://localhost:8080/api/streams/stop \
    -H "Content-Type: application/json" \
    -d "{\"streamKey\": \"$STREAM_KEY\", \"clientIp\": \"127.0.0.1\"}" &
done
wait
```

### 8.2 服务重启测试
```bash
# 重启服务并验证
podman-compose -f compose.dev.yml restart

# 等待服务启动
sleep 30

# 重新运行健康检查
./test-live-media-server.sh
```

---

## ✅ 验证结果总结

### 成功标准
- [ ] 所有容器正常运行
- [ ] 所有健康检查通过
- [ ] API 端点正常响应
- [ ] RTMP 推流成功
- [ ] 转码服务可用
- [ ] 端到端工作流完整

### 故障排除
如果任何测试失败，请检查：
1. 容器日志：`podman-compose -f compose.dev.yml logs`
2. 端口占用：`ss -tuln | grep -E "(1935|8080|8081)"`
3. 服务状态：`podman ps`
4. 网络连接：`podman network ls`

### 完成验证
当所有测试通过时，你的 Live Media Server 已经完全可用！

🎉 **恭喜！你的流媒体服务器已经完全验证并可以投入使用！**
