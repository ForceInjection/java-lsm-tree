#!/bin/bash

# =============================================================================
# Common Module - 核心配置和工具函数
# =============================================================================

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# =============================================================================
# 日志函数
# =============================================================================

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

log_test() {
    echo -e "${PURPLE}[TEST]${NC} $1"
}

log_benchmark() {
    echo -e "${CYAN}[BENCHMARK]${NC} $1"
}

# =============================================================================
# 项目配置
# =============================================================================

# 获取脚本所在目录的父目录作为项目根目录
# 注意：SCRIPT_DIR 应该指向测试套件根目录，而不是lib目录
if [ -z "${SCRIPT_DIR}" ]; then
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../" && pwd)"
fi
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
TEST_SUITE_DIR="${PROJECT_ROOT}/test-suite"
BUILD_DIR="${PROJECT_ROOT}/build"
TARGET_DIR="${PROJECT_ROOT}/target"
RESULTS_DIR="${TEST_SUITE_DIR}/results"

JACOCO_CSV="${TARGET_DIR}/site/jacoco/jacoco.csv"
CORE_CLASSES=(
  "LSMTree"
  "MemTable"
  "SSTable"
  "WriteAheadLog"
  "LeveledCompactionStrategy"
  "SizeTieredCompactionStrategy"
  "PartitionedLSMTree"
  "RangePartitionStrategy"
  "BloomFilter"
)

# 测试数据目录配置
TEST_DATA_DIR="${TEST_SUITE_DIR}/data"
TEST_DATA_FUNCTIONAL_DIR="${TEST_DATA_DIR}/functional"
TEST_DATA_PERFORMANCE_DIR="${TEST_DATA_DIR}/performance"
TEST_DATA_MEMORY_DIR="${TEST_DATA_DIR}/memory"
TEST_DATA_STRESS_DIR="${TEST_DATA_DIR}/stress"
TEST_DATA_CONCURRENT_DIR="${TEST_DATA_DIR}/concurrent"

# Java 配置
JAVA_OPTS="-Xmx2g -Xms1g -XX:+UseG1GC"
MAIN_CLASS="com.brianxiadong.lsmtree"

# 测试配置
PERFORMANCE_ITERATIONS=3

# 性能基准测试配置
BENCHMARK_OPERATIONS=${BENCHMARK_OPERATIONS:-10000}
BENCHMARK_THREADS=${BENCHMARK_THREADS:-2}
BENCHMARK_KEY_SIZE=${BENCHMARK_KEY_SIZE:-16}
BENCHMARK_VALUE_SIZE=${BENCHMARK_VALUE_SIZE:-100}

# 测试执行超时(秒)
FUNCTIONAL_EXAMPLE_TIMEOUT=${FUNCTIONAL_EXAMPLE_TIMEOUT:-20}
FUNCTIONAL_METRICS_TIMEOUT=${FUNCTIONAL_METRICS_TIMEOUT:-15}
FUNCTIONAL_API_TIMEOUT=${FUNCTIONAL_API_TIMEOUT:-20}
PERFORMANCE_TIMEOUT=${PERFORMANCE_TIMEOUT:-120}
MEMORY_TIMEOUT=${MEMORY_TIMEOUT:-30}
STRESS_TIMEOUT=${STRESS_TIMEOUT:-60}

# 动态项目信息变量（将在 get_project_info 函数中设置）
ARTIFACT_ID=""
VERSION=""
JAR_NAME=""
JAR_PATH=""

# =============================================================================
# 项目信息获取
# =============================================================================

# 获取项目信息函数（从 pom.xml 动态提取）
get_project_info() {
    local pom_file="${PROJECT_ROOT}/pom.xml"
    
    if [ ! -f "${pom_file}" ]; then
        log_error "pom.xml 文件不存在: ${pom_file}"
        return 1
    fi
    
    # 提取 artifactId
    ARTIFACT_ID=$(grep -m1 '<artifactId>' "${pom_file}" | sed 's/.*<artifactId>\(.*\)<\/artifactId>.*/\1/' | xargs)
    if [ -z "${ARTIFACT_ID}" ]; then
        log_error "无法从 pom.xml 提取 artifactId"
        return 1
    fi
    
    # 提取 version
    VERSION=$(grep -m1 '<version>' "${pom_file}" | sed 's/.*<version>\(.*\)<\/version>.*/\1/' | xargs)
    if [ -z "${VERSION}" ]; then
        log_error "无法从 pom.xml 提取 version"
        return 1
    fi
    
    # 构建 JAR 文件名和路径
    JAR_NAME="${ARTIFACT_ID}-${VERSION}.jar"
    JAR_PATH="${TARGET_DIR}/${JAR_NAME}"
    
    log_info "项目信息: artifactId=${ARTIFACT_ID}, version=${VERSION}"
    log_info "JAR 文件: ${JAR_NAME}"
    
    return 0
}

# =============================================================================
# 环境检查
# =============================================================================

# 环境检查函数
check_environment() {
    log_info "检查运行环境..."
    
    # 检查 Java 版本
    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
        log_success "Java 版本: ${JAVA_VERSION}"
    else
        log_error "Java 未安装或不在 PATH 中"
        return 1
    fi
    
    # 检查项目是否已构建（使用动态JAR路径）
    if [ ! -f "${JAR_PATH}" ]; then
        log_warning "项目未构建，JAR文件不存在: ${JAR_PATH}"
        if [ -f "${BUILD_DIR}/build.sh" ]; then
            log_info "正在构建项目..."
            "${BUILD_DIR}/build.sh" build
        elif [ -f "${PROJECT_ROOT}/build.sh" ]; then
            log_info "正在构建项目..."
            "${PROJECT_ROOT}/build.sh"
        else
            log_error "构建脚本不存在，请先运行构建"
            return 1
        fi
    else
        log_success "项目已构建"
        log_info "使用JAR文件: ${JAR_PATH}"
    fi
    
    # 检查系统资源
    if command -v free &> /dev/null; then
        MEMORY_MB=$(free -m | awk 'NR==2{printf "%.0f", $7}')
        log_info "可用内存: ${MEMORY_MB}MB"
        if [ "${MEMORY_MB}" -lt 1024 ]; then
            log_warning "可用内存较少，可能影响测试结果"
        fi
    fi
    
    # 检查磁盘空间
    DISK_SPACE=$(df "${PROJECT_ROOT}" | awk 'NR==2 {print $4}')
    log_info "可用磁盘空间: ${DISK_SPACE}KB"
    
    log_success "环境检查完成"
}

generate_jacoco_report() {
    docker run --rm \
        -v "${PROJECT_ROOT}":/workspace \
        -v "${HOME}/.m2":/root/.m2 \
        -w /workspace \
        maven:3.8.6-openjdk-8 \
        mvn -q -clean test jacoco:report >/dev/null 2>&1
}

calc_overall_line_coverage() {
    if [ ! -f "${JACOCO_CSV}" ]; then
        echo "0"
        return
    fi
    awk -F, 'NR>1 {m+=$8; c+=$9} END {t=m+c; if(t==0){print 0}else{printf "%.4f", c/t}}' "${JACOCO_CSV}"
}

calc_classes_line_coverage() {
    if [ ! -f "${JACOCO_CSV}" ]; then
        echo "0"
        return
    fi
    local cls="$1"
    awk -F, -v cls="$cls" 'NR>1 {if($3==cls || index($3, cls ".")==1){m+=$8; c+=$9}} END {t=m+c; if(t==0){print 0}else{printf "%.4f", c/t}}' "${JACOCO_CSV}"
}

write_coverage_summary_json() {
    local out="${SESSION_DIR}/coverage_summary.json"
    local overall=$(calc_overall_line_coverage)
    local details="{"
    local first=true
    for cls in "${CORE_CLASSES[@]}"; do
        local ratio=$(calc_classes_line_coverage "$cls")
        if [ "${first}" = true ]; then
            first=false
        else
            details+=" ,"
        fi
        details+="\"${cls}\": ${ratio}"
    done
    details+="}"
    cat > "${out}" <<EOF
{
  "overall_line": ${overall},
  "core_line": ${details}
}
EOF
}

enforce_coverage_gates() {
    local overall_min=${OVERALL_COVERAGE_MIN:-0.40}
    local core_min=${CORE_COVERAGE_MIN:-0.60}
    local overall=$(calc_overall_line_coverage)
    local failed=0
    if awk "BEGIN{exit !(${overall} >= ${overall_min})}"; then :; else failed=1; fi
    local core_failed_classes=()
    for cls in "${CORE_CLASSES[@]}"; do
        local ratio=$(calc_classes_line_coverage "$cls")
        if awk "BEGIN{exit !(${ratio} >= ${core_min})}"; then :; else core_failed_classes+=("$cls:${ratio}"); fi
    done
    write_coverage_summary_json
    if [ ${failed} -eq 0 ] && [ ${#core_failed_classes[@]} -eq 0 ]; then
        return 0
    fi
    if [ "${COVERAGE_FORCE}" = "true" ]; then
        log_warning "覆盖率未达标但已强制继续: overall=${overall}, core_failed=${core_failed_classes[*]}"
        return 0
    fi
    log_error "覆盖率未达标: overall=${overall} 要求>=${overall_min}; 核心模块低于${core_min}: ${core_failed_classes[*]}"
    return 1
}

# =============================================================================
# 初始化函数
# =============================================================================

# 初始化公共环境
init_common() {
    # 获取项目信息
    if ! get_project_info; then
        log_error "获取项目信息失败"
        return 1
    fi
    
    # 创建必要的目录
    mkdir -p "${RESULTS_DIR}"
    
    return 0
}

# =============================================================================
# 工具函数
# =============================================================================

# 清理测试数据 (测试数据现在保存在session目录中)
cleanup_test_data() {
    log_info "测试数据保留在session目录中，无需清理"
    # 注意：测试数据现在保存在各个session的子目录中
    # 如需清理特定session的数据，请使用 delete_session 函数
    log_success "测试数据保留完成"
}

# 检查命令是否存在
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# 获取时间戳
get_timestamp() {
    date +%Y%m%d_%H%M%S
}

# 获取ISO格式时间
get_iso_time() {
    date -Iseconds
}

# =============================================================================
# 统一测试结果管理
# =============================================================================

# 初始化测试结果文件
init_test_results() {
    local session_dir="$1"
    local results_file="${session_dir}/test_results.json"
    
    cat > "${results_file}" << 'EOF'
{
  "session_info": {
    "session_id": "",
    "start_time": "",
    "end_time": "",
    "status": "running"
  },
  "categories": {
    "unit": {
      "status": "pending",
      "start_time": null,
      "end_time": null,
      "tests": {}
    },
    "tools": {
      "status": "pending",
      "start_time": null,
      "end_time": null,
      "tests": {}
    },
    "functional": {
      "status": "pending",
      "start_time": null,
      "end_time": null,
      "tests": {}
    },
    "performance": {
      "status": "pending",
      "start_time": null,
      "end_time": null,
      "tests": {}
    },
    "memory": {
      "status": "pending",
      "start_time": null,
      "end_time": null,
      "tests": {}
    },
    "stress": {
      "status": "pending",
      "start_time": null,
      "end_time": null,
      "tests": {}
    }
  }
}
EOF
    
    # 更新session信息
    update_test_result "${results_file}" "session_info.session_id" "$(basename "${session_dir}")"
    update_test_result "${results_file}" "session_info.start_time" "$(get_iso_time)"
    
    echo "${results_file}"
}

# 更新测试结果
update_test_result() {
    local results_file="$1"
    local key="$2"
    local value="$3"
    
    # 使用临时文件进行JSON更新
    local temp_file=$(mktemp)
    
    if command_exists jq; then
        # 使用jq进行JSON更新
        jq --arg key "$key" --arg value "$value" 'setpath($key | split("."); $value)' "$results_file" > "$temp_file"
        mv "$temp_file" "$results_file"
    else
        # 简单的sed替换（备用方案）
        case "$key" in
            "session_info.session_id")
                sed -i '' "s/\"session_id\": \"[^\"]*\"/\"session_id\": \"$value\"/" "$results_file"
                ;;
            "session_info.start_time")
                sed -i '' "s/\"start_time\": \"[^\"]*\"/\"start_time\": \"$value\"/" "$results_file"
                ;;
            "session_info.end_time")
                sed -i '' "s/\"end_time\": \"[^\"]*\"/\"end_time\": \"$value\"/" "$results_file"
                ;;
            "session_info.status")
                sed -i '' "s/\"status\": \"[^\"]*\"/\"status\": \"$value\"/" "$results_file"
                ;;
            *)
                log_warning "不支持的键: $key，使用简单替换"
                ;;
        esac
    fi
}



# 开始测试类别
start_test_category() {
    local results_file="$1"
    local category="$2"
    local timestamp="$(get_iso_time)"
    
    if command_exists jq; then
        local temp_file=$(mktemp)
        jq --arg category "$category" \
           --arg status "running" \
           --arg time "$timestamp" \
           '.categories[$category].status = $status | .categories[$category].start_time = $time' \
           "$results_file" > "$temp_file"
        mv "$temp_file" "$results_file"
    else
        log_warning "jq未安装，无法更新测试类别开始状态"
    fi
}

# 记录测试结果
record_test_result() {
    local results_file="$1"
    local test_category="$2"  # functional, performance, memory, stress
    local test_name="$3"
    local result="$4"         # PASS, FAIL, SKIP
    local timestamp="$5"
    
    if [ -z "$timestamp" ]; then
        timestamp=$(get_iso_time)
    fi
    
    local temp_file=$(mktemp)
    
    if command_exists jq; then
        # 使用jq更新测试结果
        jq --arg category "$test_category" \
           --arg name "$test_name" \
           --arg result "$result" \
           --arg time "$timestamp" \
           '.categories[$category].tests[$name] = {result: $result, timestamp: $time}' \
           "$results_file" > "$temp_file"
        mv "$temp_file" "$results_file"
    else
        # 备用方案：简单的文本替换
        log_warning "jq未安装，使用简化的结果记录"
        echo "[$timestamp] $test_category.$test_name: $result" >> "${results_file}.log"
    fi
}

# 完成测试类别
complete_test_category() {
    local results_file="$1"
    local category="$2"
    local status="$3"  # completed, failed
    local timestamp="$(get_iso_time)"
    
    if command_exists jq; then
        local temp_file=$(mktemp)
        jq --arg category "$category" \
           --arg status "$status" \
           --arg time "$timestamp" \
           '.categories[$category].status = $status | .categories[$category].end_time = $time' \
           "$results_file" > "$temp_file"
        mv "$temp_file" "$results_file"
    else
        log_warning "jq未安装，无法更新测试类别状态"
    fi
}

# 完成整个测试会话
complete_test_session() {
    local results_file="$1"
    local status="$2"  # completed, failed
    
    update_test_result "$results_file" "session_info.end_time" "$(get_iso_time)"
    update_test_result "$results_file" "session_info.status" "$status"
}
