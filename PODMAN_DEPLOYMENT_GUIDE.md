# 🐳 Live Media Server Podman部署指南

## 🎯 概述

本指南详细说明如何使用Podman在Fedora系统上部署Live Media Server。相比传统的开发模式部署，容器化部署具有以下优势：

- **环境一致性**: 开发、测试、生产环境完全一致
- **简化部署**: 一键构建和启动所有服务
- **资源隔离**: 每个服务运行在独立的容器中
- **安全性**: 非root用户运行，最小化权限
- **可扩展性**: 易于水平扩展和负载均衡

## 🚀 快速开始

### 1. 环境准备
```bash
# 确保Podman已安装 (Fedora通常预装)
podman --version

# 可选：安装podman-compose
sudo dnf install podman-compose
```

### 2. 一键部署
```bash
# 克隆项目
git clone <repository-url>
cd LiveMediaServer

# 构建镜像
./podman-deploy.sh build

# 启动服务
./podman-deploy.sh start

# 检查状态
./podman-deploy.sh status
```

### 3. 验证部署
```bash
# 检查服务健康状态
curl http://localhost:8080/api/actuator/health

# 查看运行的容器
podman ps

# 查看服务日志
./podman-deploy.sh logs
```

## 📋 详细部署步骤

### Step 1: 构建容器镜像
```bash
./podman-deploy.sh build
```

这个命令会：
- 构建RTMP服务器镜像 (多阶段构建，Java编译 + 运行时)
- 构建Web API服务器镜像 (Maven构建 + Spring Boot运行时)
- 优化镜像大小和安全性

### Step 2: 启动服务
```bash
./podman-deploy.sh start
```

这个命令会：
- 创建专用网络 `live-media-net`
- 启动RTMP服务器容器 (端口1935)
- 启动Web API服务器容器 (端口8080)
- 配置卷挂载用于HLS文件共享

### Step 3: 配置OBS推流
- **服务器**: `rtmp://localhost:1935/live`
- **推流密钥**: `test-stream` (或任意标识符)

### Step 4: 测试推流
```bash
# 开始OBS推流后，检查流状态
curl http://localhost:8080/api/streams

# 查看特定流信息
curl http://localhost:8080/api/streams/test-stream
```

## 🔧 管理命令

### 服务管理
```bash
# 查看服务状态
./podman-deploy.sh status

# 重启服务
./podman-deploy.sh restart

# 停止服务
./podman-deploy.sh stop
```

### 日志管理
```bash
# 查看所有服务日志
./podman-deploy.sh logs

# 查看特定服务日志
./podman-deploy.sh logs rtmp-server
./podman-deploy.sh logs web-api-server

# 实时跟踪日志
podman logs -f rtmp-server
```

### 资源清理
```bash
# 完全清理所有资源
./podman-deploy.sh cleanup

# 这会删除：
# - 所有容器
# - 所有镜像
# - 专用网络
```

## 🐛 故障排除

### 常见问题

**1. 端口冲突**
```bash
# 检查端口占用
ss -tuln | grep -E "(1935|8080)"

# 停止冲突服务
sudo systemctl stop <conflicting-service>
```

**2. 容器启动失败**
```bash
# 查看详细错误信息
./podman-deploy.sh logs

# 检查容器状态
podman ps -a

# 重新构建镜像
./podman-deploy.sh cleanup
./podman-deploy.sh build
```

**3. 网络连接问题**
```bash
# 检查容器网络
podman network ls
podman network inspect live-media-net

# 测试容器间连通性
podman exec web-api-server ping rtmp-server
```

**4. 卷挂载问题**
```bash
# 检查卷挂载
podman inspect web-api-server | grep -A5 Mounts

# 验证媒体目录权限
ls -la media-data/
```

### 调试技巧

**进入容器调试**
```bash
# 进入RTMP服务器容器
podman exec -it rtmp-server /bin/bash

# 进入Web API服务器容器
podman exec -it web-api-server /bin/bash
```

**检查容器内部状态**
```bash
# 检查Java进程
podman exec rtmp-server ps aux | grep java

# 检查网络连接
podman exec web-api-server netstat -tuln

# 测试内部API
podman exec web-api-server curl localhost:8080/api/actuator/health
```

## 🔒 安全特性

### 容器安全
- **非root用户**: 所有容器都以非特权用户运行
- **最小化镜像**: 使用Fedora minimal基础镜像
- **健康检查**: 自动监控服务健康状态
- **网络隔离**: 服务运行在专用网络中

### 生产建议
```bash
# 启用SELinux标签
podman run --security-opt label=type:container_runtime_t ...

# 限制资源使用
podman run --memory=1g --cpus=1.0 ...

# 只读根文件系统
podman run --read-only --tmpfs /tmp ...
```

## 📊 性能优化

### 镜像优化
- 多阶段构建减少镜像大小
- 层缓存优化构建速度
- 最小化运行时依赖

### 运行时优化
```bash
# JVM调优
export JAVA_OPTS="-Xmx1g -XX:+UseG1GC -XX:+UseContainerSupport"

# 容器资源限制
podman run --memory=2g --cpus=2.0 ...
```

## 🎉 总结

通过Podman容器化部署，Live Media Server现在具备了：

✅ **生产就绪**: 完整的容器化部署方案
✅ **安全可靠**: 非root运行，资源隔离
✅ **易于管理**: 一键部署、监控、清理
✅ **高度可移植**: 跨环境一致性
✅ **可扩展性**: 为集群部署做好准备

这为后续的功能开发和生产部署奠定了坚实的基础！
