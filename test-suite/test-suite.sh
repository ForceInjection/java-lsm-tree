#!/bin/bash

# =============================================================================
# Java LSM Tree 测试套件主入口脚本
# =============================================================================
# 这是一个模块化的测试套件，包含功能测试、性能测试、内存测试和压力测试
# 
# 使用方法:
#   ./test-suite.sh [命令] [参数]
#
# 支持的命令:
#   all                    - 运行所有测试 (默认)
#   unit                   - 运行所有单元测试
#   tools                  - 运行工具与 CLI 测试
#   functional|func        - 运行功能测试
#   performance|perf       - 运行性能测试
#   memory|mem            - 运行内存测试
#   stress                - 运行压力测试
#   list                  - 列出所有测试会话
#   show [会话ID]          - 显示指定会话的详细信息
#   delete [会话ID]        - 删除指定的测试会话
#   report [会话ID]        - 重新生成指定会话的报告
#   clean                 - 清理当前测试环境
#   clean-all             - 清理所有测试数据
#   archive [天数]         - 归档指定天数前的会话
#   help                  - 显示帮助信息
# =============================================================================

# 脚本目录和项目根目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# =============================================================================
# 模块加载
# =============================================================================

# 加载公共模块
if [ -f "${SCRIPT_DIR}/lib/common.sh" ]; then
    source "${SCRIPT_DIR}/lib/common.sh"
else
    echo "错误: 无法加载公共模块 ${SCRIPT_DIR}/lib/common.sh"
    exit 1
fi

# 加载会话管理模块
if [ -f "${SCRIPT_DIR}/lib/session.sh" ]; then
    source "${SCRIPT_DIR}/lib/session.sh"
else
    log_error "无法加载会话管理模块 ${SCRIPT_DIR}/lib/session.sh"
    exit 1
fi

# 注意：tests.sh 和 reports.sh 模块将在需要时动态加载
# 因为它们依赖于 session 管理的初始化

# =============================================================================
# 动态模块加载
# =============================================================================

# 加载测试和报告模块（需要在session初始化后）
load_test_modules() {
    # 加载测试执行模块
    if [ -f "${SCRIPT_DIR}/lib/tests.sh" ]; then
        source "${SCRIPT_DIR}/lib/tests.sh"
    else
        log_error "无法加载测试执行模块 ${SCRIPT_DIR}/lib/tests.sh"
        return 1
    fi
    
    # 加载报告生成模块
    if [ -f "${SCRIPT_DIR}/lib/reports.sh" ]; then
        source "${SCRIPT_DIR}/lib/reports.sh"
    else
        log_error "无法加载报告生成模块 ${SCRIPT_DIR}/lib/reports.sh"
        return 1
    fi
    
    return 0
}

# =============================================================================
# 帮助信息
# =============================================================================

show_help() {
    cat << EOF
Java LSM Tree 测试套件

用法: $0 [命令] [参数]

命令:
  all                    运行所有测试 (默认)
  unit                   运行所有单元测试
  tools                  运行工具与 CLI 测试
  functional, func       运行功能测试
  performance, perf      运行性能测试  
  memory, mem           运行内存测试
  stress                运行压力测试
  list                  列出所有测试会话
  show [会话ID]          显示指定会话的详细信息
  delete [会话ID]        删除指定的测试会话
  report [会话ID]        重新生成指定会话的报告
  clean                 清理当前测试环境
  clean-all             清理所有测试数据
  archive [天数]         归档指定天数前的会话 (默认30天)
  help, -h, --help      显示此帮助信息

示例:
  $0                     # 运行所有测试
  $0 functional          # 只运行功能测试
  $0 performance         # 只运行性能测试
  $0 list                # 列出所有会话
  $0 show 20241201_143022 # 显示指定会话信息
  $0 delete 20241201_143022 # 删除指定会话
  $0 report 20241201_143022 # 重新生成指定会话的报告
  $0 clean               # 清理测试环境
  $0 archive 7           # 归档7天前的会话

注意:
  - 测试结果保存在 test-suite/results/ 目录下
  - 每次测试运行都会创建一个新的会话ID
  - 可以通过 'list' 命令查看所有历史会话
  - 使用 'show' 命令查看详细的测试结果
EOF
}

# =============================================================================
# 初始化和环境检查
# =============================================================================

EXIT_OVERRIDE=1
TERMINATED=false
trap 'TERMINATED=true; cleanup_test_environment; exit ${EXIT_OVERRIDE}' SIGTERM

# 轻量级初始化（用于查询类命令，不创建新会话）
init_lightweight() {
    # 只初始化公共环境，不创建新会话
    if ! init_common; then
        log_error "公共环境初始化失败"
        return 1
    fi
    
    # 设置一个临时的TEST_SESSION_ID以满足模块加载要求
    # 这个ID不会被用于创建实际的会话
    export TEST_SESSION_ID="query_mode"
    
    # 加载必要的模块但不创建会话
    if ! load_test_modules; then
        log_error "模块加载失败"
        return 1
    fi
    
    return 0
}

# 初始化测试环境
init_test_suite() {
    log_info "初始化 Java LSM Tree 测试套件..."
    
    # 初始化公共环境
    if ! init_common; then
        log_error "公共环境初始化失败"
        return 1
    fi
    
    # 初始化会话管理
    if ! init_session_management; then
        log_error "会话管理初始化失败"
        return 1
    fi
    
    # 检查环境
    if ! check_environment; then
        log_error "环境检查失败"
        return 1
    fi
    
    # 加载测试和报告模块
    if ! load_test_modules; then
        log_error "测试模块加载失败"
        return 1
    fi
    
    # 设置清理陷阱
    trap cleanup_test_environment EXIT
    
    log_success "测试套件初始化完成"
}

init_test_suite_no_gate() {
    log_info "初始化 Java LSM Tree 测试套件..."
    if ! init_common; then
        log_error "公共环境初始化失败"
        return 1
    fi
    if ! init_session_management; then
        log_error "会话管理初始化失败"
        return 1
    fi
    if ! check_environment; then
        log_error "环境检查失败"
        return 1
    fi
    if ! load_test_modules; then
        log_error "测试模块加载失败"
        return 1
    fi
    trap cleanup_test_environment EXIT
    log_success "测试套件初始化完成"
}

# =============================================================================
# 测试执行函数
# =============================================================================

# 运行完整测试套件
run_full_test_suite() {
    log_info "开始运行完整测试套件..."
    
    local start_time=$(date +%s)
    local failed_tests=()
    
    # 注意：会话管理已在init_session_management中完成
    # 这里不需要再次调用smart_session_management
    
    # 运行所有测试
    if ! run_all_tests; then
        log_error "测试执行过程中出现错误"
        failed_tests+=("execution")
    fi
    
    # 生成报告
    if ! generate_all_reports; then
        log_error "报告生成失败"
        failed_tests+=("reporting")
    fi
    
    # 完成会话
    complete_session
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    # 显示结果摘要
    log_info "测试套件执行完成"
    log_info "会话ID: ${TEST_SESSION_ID}"
    log_info "总耗时: ${duration} 秒"
    
    if [ ${#failed_tests[@]} -eq 0 ]; then
        log_success "所有测试和报告生成都已成功完成！"
        log_info "查看报告: ${SESSION_DIR}/"
        return 0
    else
        log_error "部分操作失败: ${failed_tests[*]}"
        return 1
    fi
}

# 运行单个测试类型
run_single_test() {
    local test_type="$1"
    
    log_info "开始运行 ${test_type} 测试..."
    
    # 注意：会话管理已在init_session_management中完成，这里不需要重复处理
    
    # 运行指定类型的测试
    if ! run_test_by_type "${test_type}"; then
        log_error "${test_type} 测试失败"
        return 1
    fi
    
    # 生成报告
    if ! generate_all_reports; then
        log_error "报告生成失败"
        return 1
    fi
    
    # 检查是否所有测试都已完成
    if all_tests_completed; then
        complete_session
        log_success "会话 ${TEST_SESSION_ID} 中的所有测试已完成"
    fi
    
    log_success "${test_type} 测试完成"
    log_info "会话ID: ${TEST_SESSION_ID}"
    log_info "查看报告: ${SESSION_DIR}/"
}

# =============================================================================
# 会话管理函数
# =============================================================================

# 重新生成指定会话的报告
regenerate_session_report() {
    local session_id="$1"
    
    # 处理帮助信息
    if [[ "$session_id" == "--help" || "$session_id" == "-h" ]]; then
        echo "用法: $0 report <会话ID>"
        echo ""
        echo "重新生成指定会话的测试报告"
        echo ""
        echo "参数:"
        echo "  会话ID    要重新生成报告的会话ID"
        echo ""
        echo "示例:"
        echo "  $0 report 20251026_172827    # 重新生成指定会话的报告"
        echo ""
        echo "使用 '$0 list' 命令查看可用的会话"
        return 0
    fi
    
    if [ -z "${session_id}" ]; then
        log_error "请指定会话ID"
        log_info "使用 'list' 命令查看可用的会话"
        return 1
    fi
    
    # 设置会话变量
    if ! set_session_variables_from_id "${session_id}"; then
        log_error "无法设置会话 ${session_id} 的变量"
        return 1
    fi
    
    log_info "重新生成会话 ${session_id} 的报告..."
    
    if ! generate_all_reports; then
        log_error "报告生成失败"
        return 1
    fi
    
    log_success "会话 ${session_id} 的报告已重新生成"
    log_info "查看报告: ${SESSION_DIR}/"
}

# 归档旧会话
archive_old_sessions() {
    local days="${1:-30}"
    
    # 处理帮助信息
    if [[ "$days" == "--help" || "$days" == "-h" ]]; then
        echo "用法: $0 archive [天数]"
        echo ""
        echo "归档指定天数之前的测试会话"
        echo ""
        echo "参数:"
        echo "  天数    归档多少天前的会话 (默认: 30)"
        echo ""
        echo "示例:"
        echo "  $0 archive        # 归档30天前的会话"
        echo "  $0 archive 7      # 归档7天前的会话"
        echo "  $0 archive 90     # 归档90天前的会话"
        return 0
    fi
    
    # 验证天数参数
    if ! [[ "$days" =~ ^[0-9]+$ ]]; then
        log_error "无效的天数参数: $days"
        log_info "天数必须是正整数"
        return 1
    fi
    
    log_info "归档 ${days} 天前的会话..."
    
    local sessions_dir="${RESULTS_DIR}/sessions"
    local archive_dir="${RESULTS_DIR}/archive"
    
    if [ ! -d "${sessions_dir}" ]; then
        log_warning "会话目录不存在: ${sessions_dir}"
        return 0
    fi
    
    # 创建归档目录
    mkdir -p "${archive_dir}"
    
    # 查找旧会话
    local archived_count=0
    for session_dir in "${sessions_dir}"/*/; do
        if [ -d "${session_dir}" ]; then
            local session_id=$(basename "${session_dir}")
            local session_time=$(echo "${session_id}" | cut -d'_' -f1-2 | tr '_' ' ')
            
            # 检查会话是否超过指定天数
            if [ -n "${session_time}" ]; then
                local session_timestamp=$(date -j -f "%Y%m%d %H%M%S" "${session_time}" +%s 2>/dev/null || echo "0")
                local cutoff_timestamp=$(date -j -v-${days}d +%s)
                
                if [ "${session_timestamp}" -lt "${cutoff_timestamp}" ] && [ "${session_timestamp}" -gt 0 ]; then
                    log_info "归档会话: ${session_id}"
                    mv "${session_dir}" "${archive_dir}/"
                    archived_count=$((archived_count + 1))
                fi
            fi
        fi
    done
    
    log_success "已归档 ${archived_count} 个会话到 ${archive_dir}/"
}

# =============================================================================
# 清理函数
# =============================================================================

# 清理当前测试环境
cleanup_current() {
    log_info "清理当前测试环境..."
    cleanup_test_environment
    log_success "当前测试环境清理完成"
}

# 清理所有测试数据
cleanup_all() {
    log_warning "这将删除所有测试数据和会话，包括历史记录！"
    read -p "确定要继续吗？(y/N): " -n 1 -r
    echo
    
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        log_info "清理所有测试数据..."
        
        # 清理结果目录
        if [ -d "${RESULTS_DIR}" ]; then
            rm -rf "${RESULTS_DIR}"
            log_info "已删除结果目录: ${RESULTS_DIR}"
        fi
        
        # 清理测试数据目录
        cleanup_test_data
        
        log_success "所有测试数据已清理完成"
    else
        log_info "取消清理操作"
    fi
}

# =============================================================================
# 主程序
# =============================================================================

main() {
    local command="${1:-all}"
    local param="$2"
    
    # 对于help命令，不需要初始化
    if [ "${command}" = "help" ] || [ "${command}" = "-h" ] || [ "${command}" = "--help" ]; then
        show_help
        exit 0
    fi
    
    # 查询类和清理类命令使用轻量级初始化（不创建新会话）
    case "${command}" in
        "list"|"show"|"delete"|"report"|"archive"|"clean"|"clean-all")
            if ! init_lightweight; then
                log_error "轻量级初始化失败"
                exit 1
            fi
            ;;
        "unit"|"all")
            if ! init_test_suite; then
                log_error "测试套件初始化失败"
                exit 1
            fi
            ;;
        "functional"|"func"|"tools"|"performance"|"perf"|"memory"|"mem"|"stress")
            if ! init_test_suite_no_gate; then
                log_error "测试套件初始化失败"
                exit 1
            fi
            ;;
        *)
            if ! init_test_suite; then
                log_error "测试套件初始化失败"
                exit 1
            fi
            ;;
    esac
    
    # 处理命令
    case "${command}" in
        "all")
            run_full_test_suite
            ;;
        "unit")
            run_single_test "unit"
            ;;
        "tools")
            run_single_test "tools"
            ;;
        "functional"|"func")
            run_single_test "functional"
            ;;
        "performance"|"perf")
            run_single_test "performance"
            ;;
        "memory"|"mem")
            run_single_test "memory"
            ;;
        "stress")
            run_single_test "stress"
            ;;
        "list")
            list_sessions
            ;;
        "show")
            if [ -z "${param}" ]; then
                log_error "请指定会话ID"
                log_info "使用 'list' 命令查看可用的会话"
                exit 1
            fi
            show_session "${param}"
            ;;
        "delete")
            if [ -z "${param}" ]; then
                log_error "请指定要删除的会话ID"
                log_info "使用 'list' 命令查看可用的会话"
                exit 1
            fi
            delete_session "${param}"
            ;;
        "report")
            regenerate_session_report "${param}"
            ;;
        "clean")
            cleanup_current
            ;;
        "clean-all")
            cleanup_all
            ;;
        "archive")
            archive_old_sessions "${param}"
            ;;
        *)
            log_error "未知命令: ${command}"
            echo
            show_help
            exit 1
            ;;
    esac

    local rc=$?
    EXIT_OVERRIDE=${rc}
    return ${rc}
}

# 执行主程序
main "$@"
exit $?
