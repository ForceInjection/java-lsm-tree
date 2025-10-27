#!/bin/bash

# =============================================================================
# Tests Module - 测试执行逻辑
# =============================================================================

# 确保加载了必要的模块
if [[ -z "${SCRIPT_DIR}" ]]; then
    echo "错误: 必须先加载 common.sh 模块"
    exit 1
fi

if [[ -z "${TEST_SESSION_ID}" ]]; then
    echo "错误: 必须先初始化 session 管理"
    exit 1
fi

# =============================================================================
# 功能测试
# =============================================================================

# 功能测试函数
run_functional_tests() {
    log_test "开始功能测试..."
    update_test_status "functional" "running"
    
    local results_file="${SESSION_DIR}/test_results.json"
    start_test_category "$results_file" "functional"
    
    # 运行基本功能示例
    log_test "运行基本功能示例..."
    local example_log="${SESSION_FUNCTIONAL_DIR}/example_test_$(get_timestamp).log"
    
    # 在session功能测试目录中创建测试数据目录
    local functional_data_dir="${SESSION_FUNCTIONAL_DIR}/lsm_data"
    rm -rf "${functional_data_dir}" 2>/dev/null || true
    mkdir -p "${functional_data_dir}"
    
    # 在session功能测试目录运行，传递相对路径作为数据目录
    cd "${SESSION_FUNCTIONAL_DIR}"
    java ${JAVA_OPTS} -cp "${JAR_PATH}" \
        ${MAIN_CLASS}.LSMTreeExample "lsm_data" > "${example_log}" 2>&1
    
    local exit_code=$?
    if [ ${exit_code} -eq 0 ]; then
        log_success "基本功能示例运行成功"
        record_test_result "$results_file" "functional" "example_test" "PASS"
    else
        log_error "基本功能示例运行失败 (退出码: ${exit_code})，详细信息请查看: ${example_log}"
        record_test_result "$results_file" "functional" "example_test" "FAIL"
        return 1
    fi
    
    update_test_status "functional" "completed"
    complete_test_category "$results_file" "functional" "completed"
    log_success "功能测试完成"
}

# =============================================================================
# 性能基准测试
# =============================================================================

# 性能基准测试函数
run_performance_benchmarks() {
    update_test_status "performance" "running"
    log_benchmark "开始性能基准测试..."
    
    local benchmark_log="${SESSION_PERFORMANCE_DIR}/benchmark_$(get_timestamp).log"
    local results_file="${SESSION_PERFORMANCE_DIR}/performance_results_$(get_timestamp).txt"
    local test_results_file="${SESSION_DIR}/test_results.json"
    start_test_category "$test_results_file" "performance"
    
    echo "=== Java LSM Tree 性能基准测试结果 ===" > "${results_file}"
    echo "测试时间: $(date)" >> "${results_file}"
    echo "Java 版本: $(java -version 2>&1 | head -n 1)" >> "${results_file}"
    echo "JVM 参数: ${JAVA_OPTS}" >> "${results_file}"
    echo "" >> "${results_file}"
    
    for i in $(seq 1 ${PERFORMANCE_ITERATIONS}); do
        log_benchmark "运行第 ${i} 轮性能测试..."
        
        # 在session性能测试目录中创建测试数据目录
        local performance_data_dir="${SESSION_PERFORMANCE_DIR}/benchmark_data_round_${i}"
        rm -rf "${performance_data_dir}" 2>/dev/null || true
        mkdir -p "${performance_data_dir}"
        
        cd "${SESSION_PERFORMANCE_DIR}"
        
        # 运行性能基准测试，使用命名参数格式
        echo "=== 第 ${i} 轮测试 ===" >> "${results_file}"
        java ${JAVA_OPTS} -cp "${JAR_PATH}" \
            ${MAIN_CLASS}.BenchmarkRunner \
            --operations ${BENCHMARK_OPERATIONS} \
            --threads ${BENCHMARK_THREADS} \
            --key-size ${BENCHMARK_KEY_SIZE} \
            --value-size ${BENCHMARK_VALUE_SIZE} \
            --data-dir "benchmark_data_round_${i}" >> "${results_file}" 2>&1
        
        local exit_code=$?
        if [ ${exit_code} -eq 0 ]; then
            log_success "第 ${i} 轮性能测试完成"
            record_test_result "$test_results_file" "performance" "performance_test_${i}" "PASS"
        else
            log_error "第 ${i} 轮性能测试失败 (退出码: ${exit_code})"
            record_test_result "$test_results_file" "performance" "performance_test_${i}" "FAIL"
            echo "第 ${i} 轮测试失败 (退出码: ${exit_code})" >> "${results_file}"
        fi
        
        echo "" >> "${results_file}"
        
        # 短暂休息，避免系统过载
        sleep 2
    done
    
    # 生成性能测试汇总
    generate_performance_summary
    
    update_test_status "performance" "completed"
    complete_test_category "$test_results_file" "performance" "completed"
    log_success "性能基准测试完成"
    log_info "结果文件: ${results_file}"
}

# 生成性能测试汇总
generate_performance_summary() {
    local summary_file="${SESSION_PERFORMANCE_DIR}/performance_summary.txt"
    
    echo "=== 性能测试汇总 ===" > "${summary_file}"
    echo "测试时间: $(date)" >> "${summary_file}"
    echo "测试轮数: ${PERFORMANCE_ITERATIONS}" >> "${summary_file}"
    echo "" >> "${summary_file}"
    
    for i in $(seq 1 ${PERFORMANCE_ITERATIONS}); do
        if [ -f "${SESSION_PERFORMANCE_DIR}/performance_test_${i}.result" ]; then
            local result=$(cat "${SESSION_PERFORMANCE_DIR}/performance_test_${i}.result")
            echo "第 ${i} 轮测试: ${result}" >> "${summary_file}"
        fi
    done
}

# =============================================================================
# 内存测试
# =============================================================================

# 内存使用测试函数
run_memory_tests() {
    update_test_status "memory" "running"
    log_test "开始内存使用测试..."
    
    local memory_log="${SESSION_MEMORY_DIR}/memory_test_$(get_timestamp).log"
    local memory_results="${SESSION_MEMORY_DIR}/memory_analysis_$(get_timestamp).txt"
    local test_results_file="${SESSION_DIR}/test_results.json"
    start_test_category "$test_results_file" "memory"
    
    echo "=== Java LSM Tree 内存使用测试结果 ===" > "${memory_results}"
    echo "测试时间: $(date)" >> "${memory_results}"
    echo "JVM 参数: ${JAVA_OPTS}" >> "${memory_results}"
    echo "" >> "${memory_results}"
    
    # 在session内存测试目录中创建测试数据目录
    local memory_data_dir="${SESSION_MEMORY_DIR}/lsm_data"
    rm -rf "${memory_data_dir}" 2>/dev/null || true
    mkdir -p "${memory_data_dir}"
    
    cd "${SESSION_MEMORY_DIR}"
    
    # 运行内存测试
    log_test "运行内存使用分析..."
    java ${JAVA_OPTS} -Xlog:gc:${memory_log} \
        -cp "${JAR_PATH}" ${MAIN_CLASS}.LSMTreeExample "lsm_data" \
        >> "${memory_log}" 2>&1
    
    local exit_code=$?
    if [ ${exit_code} -eq 0 ]; then
        log_success "内存测试完成"
        record_test_result "$test_results_file" "memory" "memory_test" "PASS"
        
        # 提取内存使用信息
        if [ -f "${memory_log}" ]; then
            echo "=== GC 信息 ===" >> "${memory_results}"
            grep "GC" "${memory_log}" | tail -10 >> "${memory_results}" 2>/dev/null || true
            echo "" >> "${memory_results}"
            
            echo "=== 内存使用统计 ===" >> "${memory_results}"
            grep -E "(Heap|Memory)" "${memory_log}" >> "${memory_results}" 2>/dev/null || true
        fi
    else
        log_error "内存测试失败 (退出码: ${exit_code})，详细信息请查看: ${memory_log}"
        record_test_result "$test_results_file" "memory" "memory_test" "FAIL"
        return 1
    fi
    
    update_test_status "memory" "completed"
    complete_test_category "$test_results_file" "memory" "completed"
    log_success "内存使用测试完成"
    log_info "结果文件: ${memory_results}"
}

# =============================================================================
# 压力测试
# =============================================================================

# 压力测试函数
run_stress_tests() {
    update_test_status "stress" "running"
    log_test "开始压力测试..."
    
    local stress_log="${SESSION_STRESS_DIR}/stress_test_$(get_timestamp).log"
    local stress_results="${SESSION_STRESS_DIR}/stress_analysis_$(get_timestamp).txt"
    local test_results_file="${SESSION_DIR}/test_results.json"
    start_test_category "$test_results_file" "stress"
    
    echo "=== Java LSM Tree 压力测试结果 ===" > "${stress_results}"
    echo "测试时间: $(date)" >> "${stress_results}"
    echo "JVM 参数: ${JAVA_OPTS}" >> "${stress_results}"
    echo "" >> "${stress_results}"
    
    # 在session压力测试目录中创建测试数据目录
    local stress_data_dir="${SESSION_STRESS_DIR}/stress_test_data"
    rm -rf "${stress_data_dir}" 2>/dev/null || true
    mkdir -p "${stress_data_dir}"
    
    cd "${SESSION_STRESS_DIR}"
    
    # 运行压力测试
    log_test "运行高负载压力测试..."
    
    # 设置压力测试的JVM参数
    local stress_java_opts="-Xmx1g -Xms512m -XX:+UseG1GC -XX:+PrintGCDetails"
    
    java ${stress_java_opts} -cp "${JAR_PATH}" \
        ${MAIN_CLASS}.LSMTreeExample "stress_test_data" > "${stress_log}" 2>&1
    
    local exit_code=$?
    if [ ${exit_code} -eq 0 ]; then
        log_success "压力测试完成"
        record_test_result "$test_results_file" "stress" "stress_test" "PASS"
        
        # 提取压力测试结果
        if [ -f "${stress_log}" ]; then
            echo "=== 压力测试统计 ===" >> "${stress_results}"
            grep -E "(Operations|Throughput|Latency|Error)" "${stress_log}" >> "${stress_results}" 2>/dev/null || true
            echo "" >> "${stress_results}"
            
            echo "=== GC 性能影响 ===" >> "${stress_results}"
            grep "GC" "${stress_log}" | tail -5 >> "${stress_results}" 2>/dev/null || true
        fi
    else
        log_error "压力测试失败 (退出码: ${exit_code})，详细信息请查看: ${stress_log}"
        record_test_result "$test_results_file" "stress" "stress_test" "FAIL"
        return 1
    fi
    
    update_test_status "stress" "completed"
    complete_test_category "$test_results_file" "stress" "completed"
    log_success "压力测试完成"
    log_info "结果文件: ${stress_results}"
}

# =============================================================================
# 综合测试运行器
# =============================================================================

# 运行所有测试
run_all_tests() {
    log_info "开始运行完整测试套件..."
    
    local start_time=$(date +%s)
    local failed_tests=()
    
    # 运行功能测试
    if ! run_functional_tests; then
        failed_tests+=("functional")
    fi
    
    # 运行性能测试
    if ! run_performance_benchmarks; then
        failed_tests+=("performance")
    fi
    
    # 运行内存测试
    if ! run_memory_tests; then
        failed_tests+=("memory")
    fi
    
    # 运行压力测试
    if ! run_stress_tests; then
        failed_tests+=("stress")
    fi
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    # 生成测试总结
    local summary_file="${SESSION_DIR}/test_summary.txt"
    echo "=== 测试套件执行总结 ===" > "${summary_file}"
    echo "执行时间: $(date)" >> "${summary_file}"
    echo "总耗时: ${duration} 秒" >> "${summary_file}"
    echo "会话ID: ${TEST_SESSION_ID}" >> "${summary_file}"
    echo "" >> "${summary_file}"
    
    if [ ${#failed_tests[@]} -eq 0 ]; then
        log_success "所有测试都已成功完成！"
        echo "测试结果: 全部通过" >> "${summary_file}"
        return 0
    else
        log_error "以下测试失败: ${failed_tests[*]}"
        echo "测试结果: 部分失败" >> "${summary_file}"
        echo "失败的测试: ${failed_tests[*]}" >> "${summary_file}"
        return 1
    fi
}

# =============================================================================
# 单独测试运行器
# =============================================================================

# 运行指定类型的测试
run_test_by_type() {
    local test_type="$1"
    
    case "$test_type" in
        "functional"|"func")
            run_functional_tests
            ;;
        "performance"|"perf")
            run_performance_benchmarks
            ;;
        "memory"|"mem")
            run_memory_tests
            ;;
        "stress")
            run_stress_tests
            ;;
        "all")
            run_all_tests
            ;;
        *)
            log_error "未知的测试类型: $test_type"
            log_info "支持的测试类型: functional, performance, memory, stress, all"
            return 1
            ;;
    esac
}

# =============================================================================
# 测试环境清理
# =============================================================================

# 清理测试环境
cleanup_test_environment() {
    log_info "清理测试环境..."
    
    # 清理测试数据
    cleanup_test_data
    
    # 清理临时文件
    find "${SESSION_DIR}" -name "*.tmp" -delete 2>/dev/null || true
    find "${SESSION_DIR}" -name "*.bak" -delete 2>/dev/null || true
    
    log_success "测试环境清理完成"
}