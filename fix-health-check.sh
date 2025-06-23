#!/bin/bash

# 快速修复健康检查问题的脚本

echo "🔧 修复健康检查问题"
echo "=================="

# 停止当前容器
echo "停止当前容器..."
podman stop livemediaserver_web-api-server_1 livemediaserver_rtmp-server_1 2>/dev/null || true

# 删除当前容器
echo "删除当前容器..."
podman rm livemediaserver_web-api-server_1 livemediaserver_rtmp-server_1 2>/dev/null || true

# 重新构建Web API服务器镜像（包含Actuator依赖）
echo "重新构建Web API服务器镜像..."
podman build -t live-media-server/web-api-server:latest ./web-api-server/

# 重新启动服务
echo "重新启动服务..."
./podman-deploy.sh start

echo "✅ 修复完成！"
