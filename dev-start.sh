#!/bin/bash

# Live Media Server 开发模式启动脚本
# 使用卷挂载实现快速开发迭代

echo "🚀 Live Media Server - 开发模式"
echo "================================"

# 检查podman-compose是否可用
if ! command -v podman-compose &> /dev/null; then
    echo "❌ podman-compose 未找到"
    echo "💡 请安装: pip install podman-compose"
    exit 1
fi

# 创建必要的目录
echo "📁 创建开发目录..."
mkdir -p web-api-server/logs
mkdir -p rtmp-server/bin
mkdir -p ~/.m2  # Maven缓存目录

# 停止可能运行的生产模式服务
echo "🛑 停止现有服务..."
./podman-deploy.sh stop 2>/dev/null || true

# 启动开发模式
echo "🔧 启动开发模式服务..."
echo ""
echo "📝 开发模式特性："
echo "   ✅ Spring Boot热重载 (修改Java代码自动重启)"
echo "   ✅ Maven依赖缓存 (~/.m2)"
echo "   ✅ 实时日志输出"
echo "   ✅ H2数据库控制台: http://localhost:8080/api/h2-console"
echo "   ✅ 健康检查: http://localhost:8080/api/actuator/health"
echo ""
echo "🔄 修改代码后："
echo "   - Web API: 自动重启 (几秒钟)"
echo "   - RTMP Server: 按Ctrl+C停止，然后重新运行此脚本"
echo ""
echo "⏹️  停止服务: 按 Ctrl+C"
echo ""

# 启动服务
podman-compose -f compose.dev.yml up
