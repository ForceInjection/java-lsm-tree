#!/bin/bash

# WAL分析工具启动脚本
# 用于分析LSM Tree的Write-Ahead Log文件

# 脚本目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印带颜色的消息
print_info() {
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

# 检查Java环境
check_java() {
    if ! command -v java &> /dev/null; then
        print_error "Java未安装或不在PATH中"
        exit 1
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2)
    print_info "使用Java版本: $JAVA_VERSION"
}

# 从pom.xml提取项目信息
extract_project_info() {
    if [ ! -f "$PROJECT_ROOT/pom.xml" ]; then
        print_error "未找到pom.xml文件"
        exit 1
    fi
    
    ARTIFACT_ID=$(grep -m1 '<artifactId>' "$PROJECT_ROOT/pom.xml" | sed 's/.*<artifactId>\(.*\)<\/artifactId>.*/\1/')
    VERSION=$(grep -m1 '<version>' "$PROJECT_ROOT/pom.xml" | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
    
    if [ -z "$ARTIFACT_ID" ] || [ -z "$VERSION" ]; then
        print_error "无法从pom.xml提取项目信息"
        exit 1
    fi
    
    JAR_NAME="${ARTIFACT_ID}-${VERSION}.jar"
    JAR_PATH="$PROJECT_ROOT/target/$JAR_NAME"
    
    print_info "项目: $ARTIFACT_ID"
    print_info "版本: $VERSION"
    print_info "JAR文件: $JAR_NAME"
}

# 检查并构建项目
ensure_jar_exists() {
    if [ ! -f "$JAR_PATH" ]; then
        print_warning "JAR文件不存在: $JAR_PATH"
        print_info "正在构建项目..."
        
        if [ ! -f "$PROJECT_ROOT/build.sh" ]; then
            print_error "未找到build.sh脚本"
            exit 1
        fi
        
        cd "$PROJECT_ROOT"
        if ! ./build.sh; then
            print_error "项目构建失败"
            exit 1
        fi
        
        if [ ! -f "$JAR_PATH" ]; then
            print_error "构建完成但JAR文件仍不存在: $JAR_PATH"
            exit 1
        fi
        
        print_success "项目构建成功: $JAR_PATH"
    else
        print_info "使用现有JAR文件: $JAR_PATH"
    fi
}

# 设置类路径
setup_classpath() {
    CLASSPATH="$JAR_PATH"
    
    # 添加依赖JAR（如果存在）
    DEPS_DIR="$PROJECT_ROOT/target/dependency"
    if [ -d "$DEPS_DIR" ]; then
        for jar in "$DEPS_DIR"/*.jar; do
            if [ -f "$jar" ]; then
                CLASSPATH="$CLASSPATH:$jar"
            fi
        done
    fi
    
    # 添加Maven依赖（如果存在）
    LIB_DIR="$PROJECT_ROOT/target/lib"
    if [ -d "$LIB_DIR" ]; then
        for jar in "$LIB_DIR"/*.jar; do
            if [ -f "$jar" ]; then
                CLASSPATH="$CLASSPATH:$jar"
            fi
        done
    fi
    
    print_info "类路径已设置"
}

# 运行WAL分析器
run_analyzer() {
    java -cp "$CLASSPATH" com.brianxiadong.lsmtree.tools.WALAnalyzerCLI "$@"
}

# 显示使用说明
show_usage() {
    echo "WAL分析工具 - LSM Tree写前日志分析器"
    echo ""
    echo "用法: $0 <command> [options]"
    echo ""
    echo "可用命令:"
    echo "  analyze <wal_file> [--show-entries]  - 分析WAL文件"
    echo "  validate <wal_file>                  - 验证WAL文件格式"
    echo "  export <wal_file> <output_file>      - 导出WAL数据为JSON"
    echo "  interactive                          - 进入交互模式"
    echo "  help                                 - 显示帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 analyze /path/to/wal.log"
    echo "  $0 analyze /path/to/wal.log --show-entries"
    echo "  $0 validate /path/to/wal.log"
    echo "  $0 export /path/to/wal.log /tmp/wal_data.json"
    echo "  $0 interactive"
    echo ""
    echo "注意:"
    echo "  - 如果JAR文件不存在，脚本会自动构建项目"
    echo "  - 支持相对路径和绝对路径"
    echo "  - WAL文件格式: operation|key|value|timestamp"
}

# 主函数
main() {
    # 检查参数
    if [ $# -eq 0 ]; then
        show_usage
        exit 0
    fi
    
    # 检查Java环境
    check_java
    
    # 提取项目信息
    extract_project_info
    
    # 确保JAR文件存在
    ensure_jar_exists
    
    # 设置类路径
    setup_classpath
    
    # 运行分析器
    print_info "启动WAL分析器..."
    run_analyzer "$@"
}

# 执行主函数
main "$@"