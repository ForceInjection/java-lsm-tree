#!/bin/bash

# =============================================================================
# Session Management Module - 测试会话管理
# =============================================================================

# 确保加载了 common.sh
if [[ -z "${SCRIPT_DIR}" ]]; then
    echo "错误: 必须先加载 common.sh 模块"
    exit 1
fi

# Session 相关变量
TEST_SESSION_ID=""
SESSION_DIR=""
SESSION_FUNCTIONAL_DIR=""
SESSION_PERFORMANCE_DIR=""
SESSION_MEMORY_DIR=""
SESSION_STRESS_DIR=""
SESSION_UNIT_DIR=""
SESSION_TOOLS_DIR=""
SESSION_REPORTS_DIR=""

# =============================================================================
# Session 管理 API
# =============================================================================

# 创建新的测试会话
create_session() {
    local session_id="${1:-$(get_timestamp)}"
    
    log_info "创建新的测试会话: ${session_id}"
    
    # 设置会话变量
    TEST_SESSION_ID="$session_id"
    SESSION_DIR="${RESULTS_DIR}/sessions/${TEST_SESSION_ID}"
    SESSION_FUNCTIONAL_DIR="${SESSION_DIR}/functional"
    SESSION_PERFORMANCE_DIR="${SESSION_DIR}/performance"
    SESSION_MEMORY_DIR="${SESSION_DIR}/memory"
    SESSION_STRESS_DIR="${SESSION_DIR}/stress"
    SESSION_UNIT_DIR="${SESSION_DIR}/unit"
    SESSION_TOOLS_DIR="${SESSION_DIR}/tools"
    SESSION_REPORTS_DIR="${SESSION_DIR}/reports"
    
    # 检查会话是否已存在
    if [[ -d "$SESSION_DIR" ]]; then
        log_error "会话已存在: ${session_id}"
        return 1
    fi
    
    # 创建会话目录结构
    mkdir -p "${SESSION_FUNCTIONAL_DIR}"
    mkdir -p "${SESSION_PERFORMANCE_DIR}"
    mkdir -p "${SESSION_MEMORY_DIR}"
    mkdir -p "${SESSION_STRESS_DIR}"
    mkdir -p "${SESSION_UNIT_DIR}"
    mkdir -p "${SESSION_TOOLS_DIR}"
    mkdir -p "${SESSION_REPORTS_DIR}"
    
    # 创建归档目录
    mkdir -p "${RESULTS_DIR}/archive"
    mkdir -p "${RESULTS_DIR}/templates"
    
    # 创建测试会话摘要文件
    cat > "${SESSION_DIR}/summary.json" << EOF
{
    "session_id": "${TEST_SESSION_ID}",
    "start_time": "$(get_iso_time)",
    "project": {
        "artifact_id": "${ARTIFACT_ID}",
        "version": "${VERSION}",
        "jar_name": "${JAR_NAME}"
    },
    "environment": {
        "java_version": "$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)",
        "os": "$(uname -s) $(uname -r)",
        "java_opts": "${JAVA_OPTS}"
    },
    "status": "running",
    "tests": {
        "unit": "pending",
        "tools": "pending",
        "functional": "pending",
        "performance": "pending",
        "memory": "pending",
        "stress": "pending"
    }
}
EOF
    
    # 初始化统一测试结果文件
    local results_file=$(init_test_results "${SESSION_DIR}")
    log_info "统一结果文件已创建: ${results_file}"
    
    # 更新 latest 软链接
    rm -f "${RESULTS_DIR}/sessions/latest" 2>/dev/null || true
    ln -sf "${TEST_SESSION_ID}" "${RESULTS_DIR}/sessions/latest"
    
    log_success "测试会话创建成功: ${session_id}"
    log_info "会话目录: ${SESSION_DIR}"
    
    return 0
}

# 完成测试会话
complete_session() {
    local session_id="${1:-$TEST_SESSION_ID}"
    local final_status="${2:-completed}"
    
    if [[ -z "$session_id" ]]; then
        log_error "未指定会话ID"
        return 1
    fi
    
    local session_dir="${RESULTS_DIR}/sessions/${session_id}"
    local summary_file="${session_dir}/summary.json"
    
    if [[ ! -f "$summary_file" ]]; then
        log_error "会话摘要文件不存在: ${summary_file}"
        return 1
    fi
    
    log_info "完成测试会话: ${session_id}"
    
    # 更新完成时间和状态
    local end_time=$(get_iso_time)
    local temp_file="${session_dir}/summary.json.tmp"
    local test_results_file="${session_dir}/test_results.json"
    
    # 使用 jq 或者更安全的方式更新 JSON
    if command_exists jq; then
        jq --arg end_time "$end_time" --arg status "$final_status" \
           '.end_time = $end_time | .status = $status' \
           "$summary_file" > "$temp_file" && \
           mv "$temp_file" "$summary_file"
    else
        # 如果没有 jq，使用更安全的 sed 方法
        cp "$summary_file" "$temp_file"
        sed "s/\"status\": \"[^\"]*\"/\"status\": \"$final_status\"/" "$temp_file" | \
        sed "s/\"end_time\": \"[^\"]*\"/\"end_time\": \"$end_time\"/" > "$summary_file"
        rm -f "$temp_file"
    fi
    
    # 同时更新 test_results.json 文件
    if [[ -f "$test_results_file" ]]; then
        complete_test_session "$test_results_file" "$final_status"
    fi
    
    log_success "测试会话已完成: ${session_id} (状态: ${final_status})"
    return 0
}

# 删除测试会话
delete_session() {
    local session_id="$1"
    
    # 处理帮助信息
    if [[ "$session_id" == "--help" || "$session_id" == "-h" ]]; then
        echo "用法: $0 delete <会话ID>"
        echo ""
        echo "删除指定的测试会话"
        echo ""
        echo "参数:"
        echo "  会话ID    要删除的会话ID"
        echo ""
        echo "示例:"
        echo "  $0 delete 20251026_172827    # 删除指定会话"
        echo ""
        echo "注意: 删除操作不可恢复，请谨慎使用"
        echo "使用 '$0 list' 命令查看可用的会话"
        return 0
    fi
    
    if [[ -z "$session_id" ]]; then
        log_error "请指定要删除的会话ID"
        return 1
    fi
    
    local session_dir="${RESULTS_DIR}/sessions/${session_id}"
    
    if [[ ! -d "$session_dir" ]]; then
        log_error "会话不存在: ${session_id}"
        return 1
    fi
    
    # 检查是否为当前活动会话
    local latest_session_dir="${RESULTS_DIR}/sessions/latest"
    if [[ -L "$latest_session_dir" ]]; then
        local current_session=$(readlink "$latest_session_dir")
        if [[ "$current_session" == "$session_id" ]]; then
            log_warning "正在删除当前活动会话: ${session_id}"
            rm -f "$latest_session_dir"
        fi
    fi
    
    log_info "删除测试会话: ${session_id}"
    rm -rf "$session_dir"
    
    log_success "测试会话已删除: ${session_id}"
    return 0
}

# 列出所有测试会话
list_sessions() {
    local sessions_dir="${RESULTS_DIR}/sessions"
    
    if [[ ! -d "$sessions_dir" ]]; then
        log_info "没有找到会话目录"
        return 0
    fi
    
    log_info "可用的测试会话:"
    echo ""
    
    # 获取当前活动会话
    local current_session=""
    if [[ -L "${sessions_dir}/latest" ]]; then
        current_session=$(readlink "${sessions_dir}/latest")
    fi
    
    # 列出所有会话
    local session_count=0
    for session_dir in "${sessions_dir}"/*/; do
        if [[ -d "$session_dir" ]]; then
            local session_id=$(basename "$session_dir")
            
            # 跳过 latest 软链接
            if [[ "$session_id" == "latest" ]]; then
                continue
            fi
            
            local summary_file="${session_dir}/summary.json"
            local status="unknown"
            local start_time="unknown"
            local end_time=""
            
            if [[ -f "$summary_file" ]]; then
                status=$(grep '"status"' "$summary_file" | cut -d'"' -f4 2>/dev/null || echo "unknown")
                start_time=$(grep '"start_time"' "$summary_file" | cut -d'"' -f4 2>/dev/null || echo "unknown")
                end_time=$(grep '"end_time"' "$summary_file" | cut -d'"' -f4 2>/dev/null || echo "")
            fi
            
            # 标记当前活动会话
            local marker=""
            if [[ "$session_id" == "$current_session" ]]; then
                marker=" ${GREEN}(当前)${NC}"
            fi
            
            echo -e "  ${BLUE}${session_id}${NC}${marker}"
            echo -e "    状态: ${status}"
            echo -e "    开始时间: ${start_time}"
            if [[ -n "$end_time" ]]; then
                echo -e "    结束时间: ${end_time}"
            fi
            echo ""
            
            ((session_count++))
        fi
    done
    
    if [[ $session_count -eq 0 ]]; then
        log_info "没有找到任何测试会话"
    else
        log_info "总共找到 ${session_count} 个测试会话"
    fi
    
    return 0
}

# 显示指定会话的详细信息
show_session() {
    local session_id="$1"
    
    # 处理帮助信息
    if [[ "$session_id" == "--help" || "$session_id" == "-h" ]]; then
        echo "用法: $0 show <会话ID>"
        echo ""
        echo "显示指定会话的详细信息"
        echo ""
        echo "参数:"
        echo "  会话ID    要查看的会话ID"
        echo ""
        echo "示例:"
        echo "  $0 show 20251026_172827    # 显示指定会话的详细信息"
        echo ""
        echo "使用 '$0 list' 命令查看可用的会话"
        return 0
    fi
    
    local sessions_dir="${RESULTS_DIR}/sessions"
    local session_dir="${sessions_dir}/${session_id}"
    
    if [[ ! -d "$session_dir" ]]; then
        log_error "会话 ${session_id} 不存在"
        return 1
    fi
    
    log_info "会话详细信息: ${session_id}"
    echo ""
    
    # 读取会话摘要信息
    local summary_file="${session_dir}/summary.json"
    if [[ -f "$summary_file" ]]; then
        echo "=== 基本信息 ==="
        local status=$(grep '"status"' "$summary_file" | cut -d'"' -f4 2>/dev/null || echo "unknown")
        local start_time=$(grep '"start_time"' "$summary_file" | cut -d'"' -f4 2>/dev/null || echo "unknown")
        local end_time=$(grep '"end_time"' "$summary_file" | cut -d'"' -f4 2>/dev/null || echo "")
        
        echo "会话ID: ${session_id}"
        echo "状态: ${status}"
        echo "开始时间: ${start_time}"
        if [[ -n "$end_time" ]]; then
            echo "结束时间: ${end_time}"
        fi
        echo ""
        
        echo "=== 测试状态 ==="
        if command_exists jq; then
            jq -r '.tests | to_entries[] | "\(.key): \(.value)"' "$summary_file" 2>/dev/null || {
                grep '"tests"' -A 10 "$summary_file" | grep -E '(unit|tools|functional|performance|memory|stress)' | sed 's/.*"\([^"]*\)": "\([^"]*\)".*/\1: \2/'
            }
        else
            grep '"tests"' -A 10 "$summary_file" | grep -E '(unit|tools|functional|performance|memory|stress)' | sed 's/.*"\([^"]*\)": "\([^"]*\)".*/\1: \2/'
        fi
        echo ""
    fi
    
    # 显示测试结果文件信息
    local results_file="${session_dir}/test_results.json"
    if [[ -f "$results_file" ]]; then
        echo "=== 测试结果概览 ==="
        if command_exists jq; then
            jq -r '.categories | to_entries[] | "\(.key): \(.value.status // "unknown")"' "$results_file" 2>/dev/null || {
                echo "无法解析测试结果文件"
            }
        else
            echo "测试结果文件存在: ${results_file}"
        fi
        echo ""
    fi
    
    # 显示报告文件
    local reports_dir="${session_dir}/reports"
    if [[ -d "$reports_dir" ]]; then
        echo "=== 可用报告 ==="
        for report in "${reports_dir}"/*; do
            if [[ -f "$report" ]]; then
                local report_name=$(basename "$report")
                local report_size=$(ls -lh "$report" | awk '{print $5}')
                echo "${report_name} (${report_size})"
            fi
        done
        echo ""
    fi
    
    # 显示目录结构
    echo "=== 会话目录 ==="
    echo "位置: ${session_dir}"
    if command_exists tree; then
        tree -L 2 "$session_dir" 2>/dev/null || ls -la "$session_dir"
    else
        ls -la "$session_dir"
    fi
    
    return 0
}

# 设置会话变量（从会话ID）
set_session_variables_from_id() {
    local session_id="$1"
    
    if [[ -z "$session_id" ]]; then
        log_error "未指定会话ID"
        return 1
    fi
    
    TEST_SESSION_ID="$session_id"
    SESSION_DIR="${RESULTS_DIR}/sessions/${TEST_SESSION_ID}"
    SESSION_FUNCTIONAL_DIR="${SESSION_DIR}/functional"
    SESSION_PERFORMANCE_DIR="${SESSION_DIR}/performance"
    SESSION_MEMORY_DIR="${SESSION_DIR}/memory"
    SESSION_STRESS_DIR="${SESSION_DIR}/stress"
    SESSION_UNIT_DIR="${SESSION_DIR}/unit"
    SESSION_REPORTS_DIR="${SESSION_DIR}/reports"
    
    return 0
}

# 获取会话状态
get_session_status() {
    local session_id="${1:-$TEST_SESSION_ID}"
    
    if [[ -z "$session_id" ]]; then
        echo "unknown"
        return 1
    fi
    
    local summary_file="${RESULTS_DIR}/sessions/${session_id}/summary.json"
    
    if [[ ! -f "$summary_file" ]]; then
        echo "not_found"
        return 1
    fi
    
    local status=$(grep '"status"' "$summary_file" | cut -d'"' -f4 2>/dev/null || echo "unknown")
    echo "$status"
    return 0
}

# =============================================================================
# Session 状态管理
# =============================================================================

# 更新单个测试状态
update_test_status() {
    local test_type="$1"
    local test_status="$2"
    
    if [ -f "${SESSION_DIR}/summary.json" ]; then
        local temp_file="${SESSION_DIR}/summary.json.tmp"
        
        if command_exists jq; then
            jq --arg test_type "$test_type" --arg status "$test_status" \
               '.tests[$test_type] = $status' \
               "${SESSION_DIR}/summary.json" > "$temp_file" && \
               mv "$temp_file" "${SESSION_DIR}/summary.json"
        else
            # 如果没有 jq，使用 sed 方法
            cp "${SESSION_DIR}/summary.json" "$temp_file"
            sed "s/\"${test_type}\": \"[^\"]*\"/\"${test_type}\": \"$test_status\"/" "$temp_file" > "${SESSION_DIR}/summary.json"
            rm -f "$temp_file"
        fi
    fi
}

# 检查是否所有测试都已完成
all_tests_completed() {
    if [ ! -f "${SESSION_DIR}/summary.json" ]; then
        return 1
    fi
    
    # 检查是否还有pending状态的测试
    if grep -q '"pending"' "${SESSION_DIR}/summary.json"; then
        return 1
    fi
    
    return 0
}

# =============================================================================
# Session 复用逻辑
# =============================================================================

# 检查是否存在未完成的session
has_incomplete_session() {
    local latest_session_dir="${RESULTS_DIR}/sessions/latest"
    
    # 检查是否存在latest软链接
    if [[ ! -L "$latest_session_dir" ]]; then
        return 1  # 没有现有session
    fi
    
    local session_id=$(readlink "$latest_session_dir")
    local session_path="${RESULTS_DIR}/sessions/${session_id}"
    local summary_file="${session_path}/summary.json"
    
    # 检查session目录和摘要文件是否存在
    if [[ ! -d "$session_path" ]] || [[ ! -f "$summary_file" ]]; then
        return 1  # session目录或摘要文件不存在
    fi
    
    # 检查session状态是否为running
    local session_status=$(grep '"status"' "$summary_file" | cut -d'"' -f4)
    if [[ "$session_status" == "running" ]]; then
        # 设置全局变量供其他函数使用
        EXISTING_SESSION_ID="$session_id"
        return 0  # 存在未完成的session
    fi
    
    return 1  # session已完成
}

# 询问用户是否复用现有session
ask_user_session_choice() {
    local existing_session_id="$1"
    local test_type="$2"
    
    echo ""
    log_warning "发现未完成的测试会话: ${existing_session_id}"
    
    # 显示现有session的信息
    local session_path="${RESULTS_DIR}/sessions/${existing_session_id}"
    local summary_file="${session_path}/summary.json"
    
    if [[ -f "$summary_file" ]]; then
        local start_time=$(grep '"start_time"' "$summary_file" | cut -d'"' -f4 2>/dev/null || echo "unknown")
        echo -e "  ${BLUE}会话ID:${NC} ${existing_session_id}"
        echo -e "  ${BLUE}开始时间:${NC} ${start_time}"
        
        # 显示各测试的状态
        echo -e "  ${BLUE}测试状态:${NC}"
        local functional_status=$(grep '"functional"' "$summary_file" | cut -d'"' -f4 2>/dev/null || echo "pending")
        local performance_status=$(grep '"performance"' "$summary_file" | cut -d'"' -f4 2>/dev/null || echo "pending")
        local memory_status=$(grep '"memory"' "$summary_file" | cut -d'"' -f4 2>/dev/null || echo "pending")
        local stress_status=$(grep '"stress"' "$summary_file" | cut -d'"' -f4 2>/dev/null || echo "pending")
        local unit_status=$(grep '"unit"' "$summary_file" | cut -d'"' -f4 2>/dev/null || echo "pending")
        
        echo -e "    - 单元测试: ${unit_status}"
        echo -e "    - 功能测试: ${functional_status}"
        echo -e "    - 性能测试: ${performance_status}"
        echo -e "    - 内存测试: ${memory_status}"
        echo -e "    - 压力测试: ${stress_status}"
    fi
    
    echo ""
    echo "请选择操作:"
    echo "  1) 复用现有会话 (继续在当前会话中运行测试)"
    echo "  2) 创建新会话 (开始一个全新的测试会话)"
    echo ""
    
    echo "提示: 3秒内未选择将自动使用默认选项(2)"
    
    while true; do
        read -p "请输入选择 (1 或 2) [默认:2]: " -t 3 -n 1 -r choice
        echo ""
        
        # 如果超时或输入为空，使用默认值2
        if [ $? -ne 0 ] || [ -z "$choice" ]; then
            choice="2"
            echo "超时或未输入，使用默认选项: 2"
        fi
        
        case "$choice" in
            1)
                log_info "选择复用现有会话: ${existing_session_id}"
                return 0  # 复用现有session
                ;;
            2)
                log_info "选择创建新会话"
                return 1  # 创建新session
                ;;
            *)
                echo "无效选择，请输入 1 或 2"
                ;;
        esac
    done
}

# 检查是否可以复用现有session（保持向后兼容）
can_reuse_session() {
    local test_type="$1"
    local latest_session_dir="${RESULTS_DIR}/sessions/latest"
    
    # 检查是否存在latest软链接
    if [[ ! -L "$latest_session_dir" ]]; then
        return 1  # 没有现有session
    fi
    
    local session_id=$(readlink "$latest_session_dir")
    local session_path="${RESULTS_DIR}/sessions/${session_id}"
    local summary_file="${session_path}/summary.json"
    
    # 检查session目录和摘要文件是否存在
    if [[ ! -d "$session_path" ]] || [[ ! -f "$summary_file" ]]; then
        return 1  # session目录或摘要文件不存在
    fi
    
    # 检查session状态是否为running
    local session_status=$(grep '"status"' "$summary_file" | cut -d'"' -f4)
    if [[ "$session_status" != "running" ]]; then
        return 1  # session已完成
    fi
    
    # 检查JAR文件是否发生变化
    local session_jar_name=$(grep '"jar_name"' "$summary_file" | cut -d'"' -f4)
    if [[ "$session_jar_name" != "$JAR_NAME" ]]; then
        return 1  # JAR文件名发生变化，说明重新编译了
    fi
    
    # 检查JAR文件的修改时间
    local jar_path="${TARGET_DIR}/${JAR_NAME}"
    if [[ -f "$jar_path" ]]; then
        local session_start_time=$(grep '"start_time"' "$summary_file" | cut -d'"' -f4)
        local jar_mtime=$(stat -f "%Sm" -t "%Y-%m-%dT%H:%M:%S" "$jar_path" 2>/dev/null || echo "")
        
        # 如果JAR文件比session开始时间新，说明重新编译了
        if [[ "$jar_mtime" > "$session_start_time" ]]; then
            return 1  # JAR文件已更新
        fi
    fi
    
    # 设置复用的session信息
    TEST_SESSION_ID="$session_id"
    return 0  # 可以复用
}

# 智能session管理 - 检查并询问用户
smart_session_management() {
    local test_type="$1"
    
    # 检查是否存在未完成的session
    if has_incomplete_session; then
        # 询问用户选择
        if ask_user_session_choice "$EXISTING_SESSION_ID" "$test_type"; then
            # 用户选择复用现有session
            TEST_SESSION_ID="$EXISTING_SESSION_ID"
            set_session_variables_from_id "$TEST_SESSION_ID"
            
            # 确保必要的目录存在
            mkdir -p "${SESSION_FUNCTIONAL_DIR}"
            mkdir -p "${SESSION_PERFORMANCE_DIR}"
            mkdir -p "${SESSION_MEMORY_DIR}"
            mkdir -p "${SESSION_STRESS_DIR}"
            mkdir -p "${SESSION_REPORTS_DIR}"
            
            log_success "已设置为复用现有会话: ${TEST_SESSION_ID}"
            return 0
        else
            # 用户选择创建新session
            log_info "将创建新的测试会话..."
            return 1
        fi
    else
        # 没有未完成的session，直接创建新的
        return 1
    fi
}

# =============================================================================
# 兼容性函数（保持向后兼容）
# =============================================================================

# 完成测试会话（旧版本兼容）
finalize_test_session() {
    local final_status="${1:-completed}"
    complete_session "$TEST_SESSION_ID" "$final_status"
}

# =============================================================================
# Session 初始化
# =============================================================================

# 初始化session管理
init_session_management() {
    log_info "初始化session管理..."
    
    # 使用智能session管理 - 检查并询问用户
    if ! smart_session_management "all"; then
        # 用户选择创建新session或没有未完成的session
        if ! create_session; then
            log_error "创建测试会话失败"
            return 1
        fi
    fi
    
    # 清理旧的测试数据
    cleanup_test_data
    
    log_success "Session管理初始化完成"
    log_info "测试会话ID: ${TEST_SESSION_ID}"
    log_info "结果目录: ${SESSION_DIR}"
    
    return 0
}
