#!/bin/bash

# Live Media Server Podman部署脚本
# 专为Fedora + Podman环境优化

set -e

echo "🐳 Live Media Server Podman部署脚本"
echo "=================================="

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m'

print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_header() {
    echo -e "${PURPLE}[PODMAN]${NC} $1"
}

# 检查Podman是否安装
check_podman() {
    if command -v podman &> /dev/null; then
        PODMAN_VERSION=$(podman --version)
        print_success "Podman已安装: $PODMAN_VERSION"
    else
        print_error "Podman未安装。请运行: sudo dnf install podman podman-compose"
        exit 1
    fi
}

# 检查podman-compose是否安装
check_podman_compose() {
    if command -v podman-compose &> /dev/null; then
        print_success "podman-compose已安装"
    else
        print_warning "podman-compose未安装，将使用podman命令"
        USE_COMPOSE=false
    fi
}

# 构建所有镜像
build_images() {
    print_header "构建容器镜像..."
    
    # 构建RTMP服务器镜像
    print_status "构建RTMP服务器镜像..."
    podman build --no-cache -t live-media-server/rtmp-server:latest ./rtmp-server/

    # 构建Web API服务器镜像
    print_status "构建Web API服务器镜像..."
    podman build --no-cache -t live-media-server/web-api-server:latest ./web-api-server/
    
    print_success "所有镜像构建完成"
}

# 启动服务
start_services() {
    print_header "启动Live Media Server服务..."
    
    if command -v podman-compose &> /dev/null; then
        podman-compose up -d
    else
        # 手动启动容器
        print_status "创建网络..."
        podman network create live-media-net 2>/dev/null || true
        
        print_status "启动RTMP服务器..."
        podman run -d \
            --name rtmp-server \
            --network live-media-net \
            -p 1935:1935 \
            --restart unless-stopped \
            live-media-server/rtmp-server:latest
        
        print_status "启动Web API服务器..."
        podman run -d \
            --name web-api-server \
            --network live-media-net \
            -p 8080:8080 \
            -v ./media-data:/app/media:rw \
            -e SPRING_PROFILES_ACTIVE=container \
            -e LIVEMEDIASERVER_RTMP_HOST=rtmp-server \
            -e LIVEMEDIASERVER_MEDIA_STORAGE_PATH=/app/media \
            --restart unless-stopped \
            live-media-server/web-api-server:latest
    fi
    
    print_success "服务启动完成"
}

# 停止服务
stop_services() {
    print_header "停止Live Media Server服务..."
    
    if command -v podman-compose &> /dev/null; then
        podman-compose down
    else
        podman stop rtmp-server web-api-server 2>/dev/null || true
        podman rm rtmp-server web-api-server 2>/dev/null || true
    fi
    
    print_success "服务已停止"
}

# 查看服务状态
show_status() {
    print_header "Live Media Server服务状态"
    echo ""
    
    if podman ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep -E "(rtmp-server|web-api-server)"; then
        echo ""
        print_success "服务运行正常"
        echo ""
        echo "📡 RTMP推流地址: rtmp://localhost:1935/live"
        echo "🌐 Web API地址: http://localhost:8080/api"
        echo "📊 健康检查: http://localhost:8080/api/actuator/health"
    else
        print_warning "服务未运行"
    fi
}

# 查看日志
show_logs() {
    local service=${1:-"all"}
    
    if [ "$service" = "all" ]; then
        print_header "显示所有服务日志..."
        if command -v podman-compose &> /dev/null; then
            podman-compose logs -f
        else
            podman logs -f rtmp-server &
            podman logs -f web-api-server &
            wait
        fi
    else
        print_header "显示 $service 服务日志..."
        podman logs -f "$service"
    fi
}

# 清理资源
cleanup() {
    print_header "清理Live Media Server资源..."
    
    # 停止并删除容器
    stop_services
    
    # 删除镜像
    podman rmi live-media-server/rtmp-server:latest 2>/dev/null || true
    podman rmi live-media-server/web-api-server:latest 2>/dev/null || true
    
    # 删除网络
    podman network rm live-media-net 2>/dev/null || true
    
    print_success "清理完成"
}

# 显示使用说明
show_usage() {
    echo "用法: $0 [COMMAND]"
    echo ""
    echo "命令:"
    echo "  build       构建所有容器镜像"
    echo "  start       启动所有服务"
    echo "  stop        停止所有服务"
    echo "  restart     重启所有服务"
    echo "  status      显示服务状态"
    echo "  logs        显示所有服务日志"
    echo "  logs <name> 显示指定服务日志"
    echo "  cleanup     清理所有资源"
    echo "  help        显示此帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 build && $0 start    # 构建并启动"
    echo "  $0 logs rtmp-server     # 查看RTMP服务器日志"
    echo "  $0 status               # 检查服务状态"
}

# 主逻辑
case "${1:-help}" in
    "build")
        check_podman
        build_images
        ;;
    "start")
        check_podman
        check_podman_compose
        start_services
        show_status
        ;;
    "stop")
        check_podman
        stop_services
        ;;
    "restart")
        check_podman
        check_podman_compose
        stop_services
        sleep 2
        start_services
        show_status
        ;;
    "status")
        check_podman
        show_status
        ;;
    "logs")
        check_podman
        show_logs "$2"
        ;;
    "cleanup")
        check_podman
        cleanup
        ;;
    "help"|*)
        show_usage
        ;;
esac
