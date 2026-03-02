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
# 单元测试
# =============================================================================

run_unit_tests() {
    log_test "开始单元测试..."
    update_test_status "unit" "running"
    local results_file="${SESSION_DIR}/test_results.json"
    start_test_category "$results_file" "unit"
    local unit_dir="${SESSION_UNIT_DIR}"
    mkdir -p "${unit_dir}"
    local unit_log="${unit_dir}/unit_test_$(get_timestamp).log"
    
    # 运行单元测试并生成覆盖率报告
    log_info "运行单元测试并生成覆盖率报告..."
    docker run --rm \
        -v "${PROJECT_ROOT}":/workspace \
        -v "${HOME}/.m2":/root/.m2 \
        -w /workspace \
        -e LANG=C.UTF-8 \
        -e LC_ALL=C.UTF-8 \
        -e JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8" \
        maven:3.8.6-openjdk-8 \
        bash -lc "export LANG=C.UTF-8 && export LC_ALL=C.UTF-8 && mvn -q -T 1C -Dproject.build.sourceEncoding=UTF-8 -Dproject.reporting.outputEncoding=UTF-8 -Dmaven.test.skip=false -DskipTests=false -DfailIfNoTests=false -DforkCount=1C -DreuseForks=true -DtrimStackTrace=true -Dtest.data.base.path=\"test-suite/results/sessions/${TEST_SESSION_ID}\" test jacoco:report" > "${unit_log}" 2>&1
    local exit_code=$?
    
    # 执行覆盖率门禁检查
    if ! enforce_coverage_gates; then
        log_error "覆盖率门禁未通过"
        exit_code=1
    fi
    
    if [ ${exit_code} -eq 0 ]; then
        log_success "单元测试通过"
        record_test_result "$results_file" "unit" "maven_surefire" "PASS"
    else
        log_error "单元测试失败 (退出码: ${exit_code})，详情: ${unit_log}"
        record_test_result "$results_file" "unit" "maven_surefire" "FAIL"
    fi
    
    if [ -d "${PROJECT_ROOT}/target/surefire-reports" ]; then
        mkdir -p "${unit_dir}/surefire-reports"
        cp -R "${PROJECT_ROOT}/target/surefire-reports/." "${unit_dir}/surefire-reports/" 2>/dev/null || true
    fi
    update_test_status "unit" "completed"
    complete_test_category "$results_file" "unit" "completed"
    log_success "单元测试完成"
}

run_tools_tests() {
    update_test_status "tools" "running"
    log_test "开始工具与 CLI 测试..."
    local results_file="${SESSION_DIR}/test_results.json"
    start_test_category "$results_file" "tools"
    local dir="${SESSION_TOOLS_DIR}"
    mkdir -p "${dir}"
    local logf="${dir}/tools_test_$(get_timestamp).log"
    docker run --rm \
        -v "${PROJECT_ROOT}":/workspace \
        -v "${HOME}/.m2":/root/.m2 \
        -w /workspace \
        maven:3.8.6-openjdk-8 \
        mvn -q "-Dtest=com.brianxiadong.lsmtree.tools.*" -DfailIfNoTests=false test > "${logf}" 2>&1
    local ec=$?
    if [ ${ec} -eq 0 ]; then
        record_test_result "$results_file" "tools" "maven_tools" "PASS"
    else
        record_test_result "$results_file" "tools" "maven_tools" "FAIL"
    fi
    update_test_status "tools" "completed"
    complete_test_category "$results_file" "tools" "completed"
    log_success "工具与 CLI 测试完成"
}

# =============================================================================
# 功能测试
# =============================================================================

# 功能测试函数
run_functional_tests() {
    log_test "开始功能测试..."
    update_test_status "functional" "running"
    
    local results_file="${SESSION_DIR}/test_results.json"
    start_test_category "$results_file" "functional"
    
    # 准备二级分组目录
    local functional_example_dir="${SESSION_FUNCTIONAL_DIR}/example"
    local functional_storage_dir="${SESSION_FUNCTIONAL_DIR}/storage"
    local functional_metrics_dir="${SESSION_FUNCTIONAL_DIR}/metrics"
    local functional_api_dir="${SESSION_FUNCTIONAL_DIR}/api"
    mkdir -p "${functional_example_dir}" "${functional_storage_dir}" "${functional_metrics_dir}" "${functional_api_dir}"

    # 运行基本功能示例（example 分组）
    log_test "运行基本功能示例..."
    local example_log="${functional_example_dir}/example_run_$(get_timestamp).log"
    
    # 在 example 分组目录中创建测试数据目录
    local functional_data_dir="${functional_example_dir}/lsm_data"
    rm -rf "${functional_data_dir}" 2>/dev/null || true
    mkdir -p "${functional_data_dir}"
    
    # 在session功能测试目录运行，传递相对路径作为数据目录
    cd "${functional_example_dir}"
    # 使用 Docker 运行 Maven exec 插件，确保所有依赖都可用（过滤线程警告噪音）
    docker run --rm \
        -v "${PROJECT_ROOT}":/workspace \
        -v "${HOME}/.m2":/root/.m2 \
        -w /workspace \
        maven:3.8.6-openjdk-8 \
        bash -lc "timeout ${FUNCTIONAL_EXAMPLE_TIMEOUT}s mvn exec:java -Dexec.mainClass='${MAIN_CLASS}.LSMTreeExample' -Dexec.args='test-suite/results/sessions/${TEST_SESSION_ID}/functional/example/lsm_data' -Dexec.cleanupDaemonThreads=true -Dexec.daemonThreadJoinTimeout=2000 -Dexec.stopWait=2000" 2>&1 | grep -v "\[WARNING\].*will linger\|\[WARNING\].*was interrupted\|\[WARNING\].*NOTE:.*thread\|\[WARNING\].*Couldn't destroy\|IllegalThreadStateException\|^\s\+at \|ThreadGroup.destroy\|exec.AbstractExec\|exec.daemon" > "${example_log}"
    
    local exit_code=$?
    if [ ${exit_code} -eq 0 ]; then
        log_success "基本功能示例运行成功"
        record_test_result "$results_file" "functional" "example.run" "PASS"
    else
        log_error "基本功能示例运行失败 (退出码: ${exit_code})，详细信息请查看: ${example_log}"
        record_test_result "$results_file" "functional" "example.run" "FAIL"
        return 1
    fi
    
    # storage.basic_io: 检查示例运行后数据目录存在并含有 wal.log
    if [ -f "${functional_data_dir}/wal.log" ] || ls "${functional_data_dir}"/sstable_* 1>/dev/null 2>&1; then
        record_test_result "$results_file" "functional" "storage.basic_io" "PASS"
    else
        record_test_result "$results_file" "functional" "storage.basic_io" "FAIL"
    fi

    # metrics.expose: 启用指标开关运行一次程序（不校验网络连通性，仅校验可启动）
    local metrics_log="${functional_metrics_dir}/metrics_run_$(get_timestamp).log"
    cd "${functional_metrics_dir}"
    docker run --rm \
        -v "${PROJECT_ROOT}":/workspace \
        -v "${HOME}/.m2":/root/.m2 \
        -w /workspace \
        -e MAVEN_OPTS="-Dlsm.metrics.http.enabled=true -Dlsm.metrics.http.port=9093" \
        maven:3.8.6-openjdk-8 \
        bash -lc "timeout ${FUNCTIONAL_METRICS_TIMEOUT}s mvn exec:java -Dexec.mainClass='${MAIN_CLASS}.LSMTreeExample' -Dexec.args='test-suite/results/sessions/${TEST_SESSION_ID}/functional/example/lsm_data' -Dexec.cleanupDaemonThreads=true -Dexec.daemonThreadJoinTimeout=2000 -Dexec.stopWait=2000" 2>&1 | grep -v "\[WARNING\].*will linger\|\[WARNING\].*was interrupted\|\[WARNING\].*NOTE:.*thread\|\[WARNING\].*Couldn't destroy\|IllegalThreadStateException\|^\s\+at \|ThreadGroup.destroy\|exec.AbstractExec\|exec.daemon" > "${metrics_log}"
    if [ $? -eq 0 ]; then
        record_test_result "$results_file" "functional" "metrics.expose" "PASS"
    else
        record_test_result "$results_file" "functional" "metrics.expose" "FAIL"
    fi

    # api.put_get: 使用 BenchmarkRunner 以极小规模运行，验证 API 路径可用（过滤线程警告噪音）
    local api_log="${functional_api_dir}/api_run_$(get_timestamp).log"
    cd "${functional_api_dir}"
    docker run --rm \
        -v "${PROJECT_ROOT}":/workspace \
        -v "${SESSION_DIR}":/session \
        -v "${HOME}/.m2":/root/.m2 \
        -w /workspace \
        maven:3.8.6-openjdk-8 \
        bash -lc "timeout ${FUNCTIONAL_API_TIMEOUT}s mvn exec:java -Dexec.mainClass='${MAIN_CLASS}.BenchmarkRunner' -Dexec.args='--operations 50 --threads 1 --key-size 8 --value-size 16 --data-dir test-suite/results/sessions/${TEST_SESSION_ID}/functional/api_data' -Dexec.cleanupDaemonThreads=true -Dexec.daemonThreadJoinTimeout=2000 -Dexec.stopWait=2000" 2>&1 | grep -v "\[WARNING\].*will linger\|\[WARNING\].*was interrupted\|\[WARNING\].*NOTE:.*thread\|\[WARNING\].*Couldn't destroy\|IllegalThreadStateException\|^\s\+at \|ThreadGroup.destroy\|exec.AbstractExec\|exec.daemon" > "${api_log}"
    if [ $? -eq 0 ]; then
        record_test_result "$results_file" "functional" "api.put_get" "PASS"
    else
        record_test_result "$results_file" "functional" "api.put_get" "FAIL"
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
        
        # 运行性能基准测试，使用命名参数格式（过滤线程警告噪音）
        echo "=== 第 ${i} 轮测试 ===" >> "${results_file}"
        docker run --rm \
            -v "${PROJECT_ROOT}":/workspace \
            -v "${HOME}/.m2":/root/.m2 \
            -w /workspace \
            maven:3.8.6-openjdk-8 \
            bash -lc "timeout ${PERFORMANCE_TIMEOUT}s mvn exec:java -Dexec.mainClass='${MAIN_CLASS}.BenchmarkRunner' -Dexec.args='--operations ${BENCHMARK_OPERATIONS} --threads ${BENCHMARK_THREADS} --key-size ${BENCHMARK_KEY_SIZE} --value-size ${BENCHMARK_VALUE_SIZE} --memtable-threshold ${BENCHMARK_MEMTABLE_THRESHOLD} --data-dir test-suite/results/sessions/${TEST_SESSION_ID}/performance/benchmark_data_round_${i}' -Dexec.cleanupDaemonThreads=true -Dexec.daemonThreadJoinTimeout=2000 -Dexec.stopWait=2000" 2>&1 | grep -v "\[WARNING\].*will linger\|\[WARNING\].*was interrupted\|\[WARNING\].*NOTE:.*thread\|\[WARNING\].*Couldn't destroy\|IllegalThreadStateException\|^\s\+at \|ThreadGroup.destroy\|exec.AbstractExec\|exec.daemon" >> "${results_file}"
        
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

# 仅运行缓存对比基准测试
run_cache_only_benchmark() {
    update_test_status "performance" "running"
    log_benchmark "仅运行缓存对比基准测试..."

    local test_results_file="${SESSION_DIR}/test_results.json"
    start_test_category "$test_results_file" "performance"

    local cache_dir="${SESSION_PERFORMANCE_DIR}"
    mkdir -p "${cache_dir}"
    local cache_results_file="${cache_dir}/cache_benchmark_only_$(get_timestamp).txt"

    docker run --rm \
        -v "${PROJECT_ROOT}":/workspace \
        -v "${HOME}/.m2":/root/.m2 \
        -w /workspace \
        maven:3.8.6-openjdk-8 \
        bash -lc "timeout ${PERFORMANCE_TIMEOUT}s mvn exec:java -Dexec.mainClass='${MAIN_CLASS}.BenchmarkRunner' -Dexec.args='--operations ${BENCHMARK_OPERATIONS} --threads ${BENCHMARK_THREADS} --key-size ${BENCHMARK_KEY_SIZE} --value-size ${BENCHMARK_VALUE_SIZE} --memtable-threshold ${BENCHMARK_MEMTABLE_THRESHOLD} --data-dir test-suite/results/sessions/${TEST_SESSION_ID}/performance/cache_only --only-cache-benchmark' -Dexec.cleanupDaemonThreads=true -Dexec.daemonThreadJoinTimeout=2000 -Dexec.stopWait=2000" 2>&1 | grep -v "\[WARNING\].*will linger\|\[WARNING\].*was interrupted\|\[WARNING\].*NOTE:.*thread\|\[WARNING\].*Couldn't destroy\|IllegalThreadStateException\|^\s\+at \|ThreadGroup.destroy\|exec.AbstractExec\|exec.daemon" >> "${cache_results_file}"
    if [ $? -eq 0 ]; then
        record_test_result "$test_results_file" "performance" "cache_vs_no_cache" "PASS"
    else
        record_test_result "$test_results_file" "performance" "cache_vs_no_cache" "FAIL"
    fi

    update_test_status "performance" "completed"
    complete_test_category "$test_results_file" "performance" "completed"
    log_success "缓存对比基准测试完成"
    log_info "结果文件: ${cache_results_file}"
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
    
    log_test "运行内存使用分析..."
    docker run --rm \
        -v "${PROJECT_ROOT}":/workspace \
        -v "${HOME}/.m2":/root/.m2 \
        -w /workspace \
        maven:3.8.6-openjdk-8 \
        bash -lc "timeout ${MEMORY_TIMEOUT}s mvn -q exec:java -Dexec.mainClass='${MAIN_CLASS}.LSMTreeExample' -Dexec.args='test-suite/results/sessions/${TEST_SESSION_ID}/memory/lsm_data' -Dexec.jvmArgs='-Xlog:gc:${memory_log} ${JAVA_OPTS}' -Dexec.cleanupDaemonThreads=true -Dexec.daemonThreadJoinTimeout=2000 -Dexec.stopWait=2000" 2>&1 | grep -v "\[WARNING\].*will linger\|\[WARNING\].*was interrupted\|\[WARNING\].*NOTE:.*thread\|\[WARNING\].*Couldn't destroy\|IllegalThreadStateException\|^\s\+at \|ThreadGroup.destroy\|exec.AbstractExec\|exec.daemon" >> "${memory_log}"
    
    local exit_code=$?
    if [ ${exit_code} -eq 0 ]; then
        log_success "内存测试完成"
        record_test_result "$test_results_file" "memory" "memory_test" "PASS"
        
        # 提取内存使用信息
        if [ -f "${memory_log}" ]; then
            echo "=== 测试执行统计 ===" >> "${memory_results}"
            # 提取关键性能指标
            grep -E "(插入.*耗时|查询.*耗时|统计信息)" "${memory_log}" >> "${memory_results}" 2>/dev/null || true
            echo "" >> "${memory_results}"
            
            echo "=== LSM Tree 状态 ===" >> "${memory_results}"
            grep -E "LSMTreeStats" "${memory_log}" >> "${memory_results}" 2>/dev/null || true
            echo "" >> "${memory_results}"
            
            # 提取刷盘信息
            echo "=== 刷盘信息 ===" >> "${memory_results}"
            grep -E "(刷盘|flush)" "${memory_log}" >> "${memory_results}" 2>/dev/null || true
            
            # 计算 JVM 运行时内存信息
            echo "" >> "${memory_results}"
            echo "=== JVM 内存估算 ===" >> "${memory_results}"
            echo "测试数据量: 10000 条记录" >> "${memory_results}"
            echo "内存配置: ${JAVA_OPTS}" >> "${memory_results}"
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
# 内存优化专项测试
# =============================================================================

# 内存优化百万级数据测试函数
run_memory_optimization_tests() {
    update_test_status "memory" "running"
    log_test "开始内存优化专项测试 (百万级数据)..."
    
    local memory_opt_log="${SESSION_MEMORY_DIR}/memory_optimization_test_$(get_timestamp).log"
    local memory_opt_results="${SESSION_MEMORY_DIR}/memory_optimization_analysis_$(get_timestamp).txt"
    local test_results_file="${SESSION_DIR}/test_results.json"
    start_test_category "$test_results_file" "memory"
    
    echo "=== Java LSM Tree 内存优化专项测试结果 ===" > "${memory_opt_results}"
    echo "测试时间: $(date)" >> "${memory_opt_results}"
    echo "测试规模: 百万级数据 (1,000,000 records)" >> "${memory_opt_results}"
    echo "JVM 参数: ${JAVA_OPTS}" >> "${memory_opt_results}"
    echo "" >> "${memory_opt_results}"
    
    # 在session内存测试目录中创建优化测试数据目录
    local memory_opt_data_dir="${SESSION_MEMORY_DIR}/memory_opt_data"
    rm -rf "${memory_opt_data_dir}" 2>/dev/null || true
    mkdir -p "${memory_opt_data_dir}"
    
    cd "${SESSION_MEMORY_DIR}"
    
    log_test "运行内存优化百万级数据测试..."
    
    # 使用更大的JVM堆内存设置
    local opt_java_opts="-Xmx4g -Xms2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+PrintGCDetails -XX:+PrintGCTimeStamps"
    
    docker run --rm \
        -v "${PROJECT_ROOT}":/workspace \
        -v "${HOME}/.m2":/root/.m2 \
        -w /workspace \
        maven:3.8.6-openjdk-8 \
        bash -lc "timeout ${MEMORY_TIMEOUT}s mvn -q exec:java -Dexec.mainClass='${MAIN_CLASS}.memory.ImprovedMemoryMeasurement' -Dexec.jvmArgs='${opt_java_opts}' -Dexec.cleanupDaemonThreads=true -Dexec.daemonThreadJoinTimeout=2000 -Dexec.stopWait=2000" 2>&1 | grep -v "\[WARNING\].*will linger\|\[WARNING\].*was interrupted\|\[WARNING\].*NOTE:.*thread\|\[WARNING\].*Couldn't destroy\|IllegalThreadStateException\|^\s\+at \|ThreadGroup.destroy\|exec.AbstractExec\|exec.daemon" > "${memory_opt_log}"
    
    local exit_code=$?
    if [ ${exit_code} -eq 0 ]; then
        log_success "内存优化测试完成"
        record_test_result "$test_results_file" "memory" "memory_optimization_million" "PASS"
        
        # 分析测试结果
        if [ -f "${memory_opt_log}" ]; then
            echo "=== 内存优化测试详细结果 ===" >> "${memory_opt_results}"
            
            # 提取性能提升数据
            echo "--- 性能提升分析 ---" >> "${memory_opt_results}"
            grep -E "(性能改进|时间提升)" "${memory_opt_log}" >> "${memory_opt_results}" 2>/dev/null || echo "未找到性能提升数据" >> "${memory_opt_results}"
            
            # 提取GC压力减少数据
            echo -e "\n--- GC压力减少分析 ---" >> "${memory_opt_results}"
            grep -E "(GC压力减少|GC次数增加)" "${memory_opt_log}" >> "${memory_opt_results}" 2>/dev/null || echo "未找到GC数据" >> "${memory_opt_results}"
            
            # 提取对象池统计
            echo -e "\n--- 对象池使用统计 ---" >> "${memory_opt_results}"
            grep -E "对象池统计" "${memory_opt_log}" >> "${memory_opt_results}" 2>/dev/null || echo "未找到对象池统计" >> "${memory_opt_results}"
            
            # 提取内存使用数据
            echo -e "\n--- 内存使用详情 ---" >> "${memory_opt_results}"
            grep -E "(堆内存增长|非堆内存增长|堆内存使用率)" "${memory_opt_log}" >> "${memory_opt_results}" 2>/dev/null || echo "未找到内存使用详情" >> "${memory_opt_results}"
        fi
    else
        log_error "内存优化测试失败 (退出码: ${exit_code})，详细信息请查看: ${memory_opt_log}"
        record_test_result "$test_results_file" "memory" "memory_optimization_million" "FAIL"
        
        # 记录错误信息
        echo "=== 测试失败信息 ===" >> "${memory_opt_results}"
        echo "退出码: ${exit_code}" >> "${memory_opt_results}"
        echo "错误日志:" >> "${memory_opt_results}"
        tail -20 "${memory_opt_log}" >> "${memory_opt_results}" 2>/dev/null || echo "无法读取错误日志" >> "${memory_opt_results}"
        return 1
    fi
    
    update_test_status "memory" "completed"
    complete_test_category "$test_results_file" "memory" "completed"
    log_success "内存优化专项测试完成"
    log_info "结果文件: ${memory_opt_results}"
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
    
    # 使用 BenchmarkRunner 进行压力测试，获取详细性能指标（过滤线程警告噪音）
    docker run --rm \
        -v "${PROJECT_ROOT}":/workspace \
        -v "${HOME}/.m2":/root/.m2 \
        -w /workspace \
        maven:3.8.6-openjdk-8 \
        bash -lc "timeout ${STRESS_TIMEOUT}s mvn exec:java -Dexec.mainClass='${MAIN_CLASS}.BenchmarkRunner' -Dexec.args='--operations ${STRESS_OPERATIONS:-20000} --threads ${STRESS_THREADS:-4} --key-size 16 --value-size 100 --data-dir test-suite/results/sessions/${TEST_SESSION_ID}/stress/stress_test_data' -Dexec.cleanupDaemonThreads=true -Dexec.daemonThreadJoinTimeout=2000 -Dexec.stopWait=2000" 2>&1 | grep -v "\[WARNING\].*will linger\|\[WARNING\].*was interrupted\|\[WARNING\].*NOTE:.*thread\|\[WARNING\].*Couldn't destroy\|IllegalThreadStateException\|^\s\+at \|ThreadGroup.destroy\|exec.AbstractExec\|exec.daemon" > "${stress_log}"
    
    local exit_code=$?
    if [ ${exit_code} -eq 0 ]; then
        log_success "压力测试完成"
        record_test_result "$test_results_file" "stress" "stress_test" "PASS"
        
        # 提取压力测试结果
        if [ -f "${stress_log}" ]; then
            echo "=== 压力测试统计 ===" >> "${stress_results}"
            # 提取关键性能指标
            grep -E "(吞吐量|总操作数|错误数|延迟|Throughput|ops/sec)" "${stress_log}" >> "${stress_results}" 2>/dev/null || true
            echo "" >> "${stress_results}"
            
            echo "=== 写入性能 ===" >> "${stress_results}"
            grep -E "(顺序写入|随机写入|写入延迟)" "${stress_log}" >> "${stress_results}" 2>/dev/null || true
            echo "" >> "${stress_results}"
            
            echo "=== 读取性能 ===" >> "${stress_results}"
            grep -E "(读取|缓存对比|命中)" "${stress_log}" >> "${stress_results}" 2>/dev/null || true
            echo "" >> "${stress_results}"
            
            echo "=== 混合工作负载 ===" >> "${stress_results}"
            grep -E "(混合|工作负载)" "${stress_log}" >> "${stress_results}" 2>/dev/null || true
            echo "" >> "${stress_results}"
            
            echo "=== LSM Tree 状态 ===" >> "${stress_results}"
            grep -E "LSMTreeStats" "${stress_log}" >> "${stress_results}" 2>/dev/null || true
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
    
    if ! run_unit_tests; then
        failed_tests+=("unit")
    fi
    if ! run_tools_tests; then
        failed_tests+=("tools")
    fi
    
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
        "unit")
            run_unit_tests
            ;;
        "tools")
            run_tools_tests
            ;;
        "functional"|"func")
            run_functional_tests
            ;;
        "performance"|"perf")
            run_performance_benchmarks
            ;;
        "cache"|"cache-benchmark"|"cbench")
            run_cache_only_benchmark
            ;;
        "memory"|"mem")
            run_memory_tests
            ;;
        "memory-opt"|"mem-opt"|"million")
            run_memory_optimization_tests
            ;;
        "stress")
            run_stress_tests
            ;;
        "all")
            run_all_tests
            ;;
        *)
            log_error "未知的测试类型: $test_type"
            log_info "支持的测试类型: unit, tools, functional, performance, cache, memory, memory-opt, stress, all"
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
