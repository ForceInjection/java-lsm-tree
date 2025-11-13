#!/bin/bash

# Java LSM Tree 项目构建脚本
# 使用 Maven Docker 镜像构建项目

set -e  # 遇到错误时退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 项目根目录
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="${PROJECT_ROOT}/build"
TARGET_DIR="${PROJECT_ROOT}/target"

# Maven 镜像配置
MAVEN_IMAGE="maven:3.8.6-openjdk-8"
CONTAINER_NAME="lsm-tree-build"

# 清理函数
cleanup() {
    log_info "清理构建环境..."
    rm -rf "${BUILD_DIR}/logs"
    docker rm -f ${CONTAINER_NAME} 2>/dev/null || true
}

# 设置清理陷阱
trap cleanup EXIT

# 从pom.xml提取项目信息
get_project_info() {
    local pom_file="${PROJECT_ROOT}/pom.xml"
    
    if [ ! -f "$pom_file" ]; then
        log_error "未找到pom.xml文件: $pom_file"
        exit 1
    fi
    
    # 提取artifactId和version
    ARTIFACT_ID=$(grep -o '<artifactId>[^<]*</artifactId>' "$pom_file" | head -1 | sed 's/<[^>]*>//g')
    VERSION=$(grep -o '<version>[^<]*</version>' "$pom_file" | head -1 | sed 's/<[^>]*>//g')
    
    if [ -z "$ARTIFACT_ID" ] || [ -z "$VERSION" ]; then
        log_error "无法从pom.xml中提取项目信息"
        exit 1
    fi
    
    # 构建jar文件名
    JAR_NAME="${ARTIFACT_ID}-${VERSION}.jar"
    JAR_PATH="${TARGET_DIR}/${JAR_NAME}"
    
    log_info "项目信息: artifactId=${ARTIFACT_ID}, version=${VERSION}"
    log_info "预期jar文件: ${JAR_NAME}"
}

# 主构建函数
build_project() {
    log_info "开始构建 Java LSM Tree 项目..."
    
    # 检查项目根目录
    if [ ! -f "${PROJECT_ROOT}/pom.xml" ]; then
        log_error "未找到 pom.xml 文件，请确认在正确的项目目录中"
        exit 1
    fi
    
    # 提取项目信息
    get_project_info
    
    # 检查 Docker 是否可用
    if ! command -v docker &> /dev/null; then
        log_error "Docker 未安装或不可用，请先安装 Docker"
        exit 1
    fi
    
    # 检查 Docker 是否运行
    if ! docker info &> /dev/null; then
        log_error "Docker 服务未运行，请启动 Docker"
        exit 1
    fi
    
    # 拉取 Maven 镜像
    log_info "拉取 Maven 镜像..."
    docker pull ${MAVEN_IMAGE}
    
    # 创建构建输出目录
    mkdir -p "${BUILD_DIR}/logs"
    mkdir -p "${TARGET_DIR}"
    
    # 运行 Maven 构建
    log_info "执行 Maven 构建..."
    docker run --rm \
        --name ${CONTAINER_NAME} \
        -v "${PROJECT_ROOT}":/workspace \
        -v "${HOME}/.m2":/root/.m2 \
        -w /workspace \
        ${MAVEN_IMAGE} \
        mvn clean compile package -DskipTests=true \
        | tee "${BUILD_DIR}/logs/build-$(date +%Y%m%d-%H%M%S).log"
    
    # 检查构建结果
    if [ $? -eq 0 ]; then
        log_success "项目构建成功！"
        
        # 检查构建产物
        if [ -f "${JAR_PATH}" ]; then
            log_success "构建产物已生成: ${JAR_PATH}"
        else
            log_warning "未找到预期的jar文件: ${JAR_NAME}，检查target目录中的其他文件..."
            ls -la "${TARGET_DIR}/" || true
        fi
        
        # 显示构建信息
        log_info "构建信息:"
        echo "  - Java 版本: OpenJDK 8"
        echo "  - Maven 版本: 3.8.6"
        echo "  - 构建时间: $(date)"
        echo "  - 构建日志: ${BUILD_DIR}/logs/"
        echo "  - 构建产物: ${TARGET_DIR}/"
        
    else
        log_error "项目构建失败！请检查构建日志"
        exit 1
    fi
}

# 运行测试函数
run_tests() {
    log_info "运行项目测试..."
    docker run --rm \
        --name ${CONTAINER_NAME}-test \
        -v "${PROJECT_ROOT}":/workspace \
        -v "${HOME}/.m2":/root/.m2 \
        -w /workspace \
        ${MAVEN_IMAGE} \
        mvn test \
        | tee "${BUILD_DIR}/logs/test-$(date +%Y%m%d-%H%M%S).log"
    
    if [ $? -eq 0 ]; then
        log_success "所有测试通过！"
    else
        log_error "测试失败！请检查测试日志"
        exit 1
    fi
}

# 清理构建产物函数
clean_build() {
    log_info "完整清理构建产物..."
    docker run --rm \
        -v "${PROJECT_ROOT}":/workspace \
        -v "${HOME}/.m2":/root/.m2 \
        -w /workspace \
        ${MAVEN_IMAGE} \
        mvn clean

    rm -rf "${BUILD_DIR}/logs"
    log_success "构建产物清理完成"
}

# 显示帮助信息
show_help() {
    echo "Java LSM Tree 构建脚本"
    echo ""
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  build     构建项目（默认）"
    echo "  test      运行测试"
    echo "  clean     清理构建产物"
    echo "  help      显示帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 build    # 构建项目"
    echo "  $0 test     # 运行测试"
    echo "  $0 clean    # 清理构建产物"
}

# 主程序
main() {
    case "${1:-build}" in
        "build")
            build_project
            ;;
        "test")
            run_tests
            ;;
        "clean")
            clean_build
            ;;
        "help"|"-h"|"--help")
            show_help
            ;;
        *)
            log_error "未知选项: $1"
            show_help
            exit 1
            ;;
    esac
}

# 执行主程序
main "$@"