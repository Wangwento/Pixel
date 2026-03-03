#!/bin/bash
# =====================================================
# Pixel 中间件启动脚本
# =====================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查Docker
check_docker() {
    if ! command -v docker &> /dev/null; then
        print_error "Docker未安装，请先安装Docker"
        exit 1
    fi
    if ! docker info &> /dev/null 2>&1; then
        print_error "Docker未运行，请先启动Docker"
        exit 1
    fi
    print_info "Docker检查通过"
}

# 启动所有服务
start_all() {
    print_info "启动所有中间件..."
    docker compose up -d
    print_info "等待服务启动..."
    sleep 10
    show_status
}

# 停止所有服务
stop_all() {
    print_info "停止所有中间件..."
    docker compose down
}

# 重启所有服务
restart_all() {
    stop_all
    start_all
}

# 显示状态
show_status() {
    echo ""
    print_info "===== 服务状态 ====="
    docker compose ps
    echo ""
    print_info "===== 服务地址 ====="
    echo "  MySQL:      localhost:3306  (root/wang331333)"
    echo "  Redis:      localhost:6379"
    echo "  Nacos:      http://localhost:8848/nacos  (nacos/nacos)"
    echo "  Kafka:      localhost:9094 (外部访问)"
    echo "  Kafka UI:   http://localhost:8089"
    echo "  MinIO:      http://localhost:9001  (minioadmin/minioadmin123)"
    echo ""
}

# 查看日志
show_logs() {
    if [ -z "$1" ]; then
        docker compose logs -f --tail=100
    else
        docker compose logs -f --tail=100 "$1"
    fi
}

# 清理数据
clean_data() {
    print_warn "警告: 这将删除所有数据卷！"
    read -p "确认删除? (y/N): " confirm
    if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
        docker compose down -v
        print_info "数据已清理"
    else
        print_info "取消操作"
    fi
}

# 帮助信息
show_help() {
    echo "用法: $0 <command>"
    echo ""
    echo "命令:"
    echo "  start       启动所有中间件"
    echo "  stop        停止所有中间件"
    echo "  restart     重启所有中间件"
    echo "  status      查看服务状态"
    echo "  logs [svc]  查看日志 (可指定服务名)"
    echo "  clean       清理所有数据"
    echo "  help        显示帮助"
    echo ""
    echo "示例:"
    echo "  $0 start          # 启动所有"
    echo "  $0 logs nacos     # 查看Nacos日志"
    echo "  $0 logs mysql     # 查看MySQL日志"
}

# 主入口
case "$1" in
    start)
        check_docker
        start_all
        ;;
    stop)
        stop_all
        ;;
    restart)
        check_docker
        restart_all
        ;;
    status)
        show_status
        ;;
    logs)
        show_logs "$2"
        ;;
    clean)
        clean_data
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        show_help
        exit 1
        ;;
esac
