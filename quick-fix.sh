#!/bin/bash

# 快速修复健康检查问题

echo "🔧 快速修复健康检查问题"
echo "======================"

# 停止当前容器
echo "停止当前容器..."
podman stop livemediaserver_rtmp-server_1 2>/dev/null || true

# 删除当前容器
echo "删除当前容器..."
podman rm livemediaserver_rtmp-server_1 2>/dev/null || true

# 重新构建RTMP服务器镜像（添加net-tools）
echo "重新构建RTMP服务器镜像..."
podman build -t live-media-server/rtmp-server:latest ./rtmp-server/

# 重新启动RTMP服务器
echo "重新启动RTMP服务器..."
podman run -d \
  --name livemediaserver_rtmp-server_1 \
  -p 1935:1935 \
  live-media-server/rtmp-server:latest

echo "✅ 修复完成！"

# 等待一会儿然后检查状态
echo "等待服务启动..."
sleep 10

echo "检查容器状态："
podman ps

echo ""
echo "检查健康状态："
sleep 20
podman ps
