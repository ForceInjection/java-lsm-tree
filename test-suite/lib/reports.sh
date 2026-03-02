#!/bin/bash

# =============================================================================
# Reports Module - 报告生成逻辑
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
# 统一结果格式处理函数
# =============================================================================

# 从统一的JSON结果文件中读取测试类别状态
get_category_status_from_json() {
    local results_file="$1"
    local category="$2"
    
    if [ ! -f "$results_file" ]; then
        echo "not_run"
        return
    fi
    
    # 使用jq读取类别状态
    local status=$(jq -r ".categories.${category}.status // \"not_run\"" "$results_file" 2>/dev/null)
    echo "$status"
}

# 从统一的JSON结果文件中读取测试结果
get_test_results_from_json() {
    local results_file="$1"
    local category="$2"
    
    if [ ! -f "$results_file" ]; then
        echo "{}"
        return
    fi
    
    # 使用jq读取测试结果
    local results=$(jq -r ".categories.${category}.tests // {}" "$results_file" 2>/dev/null)
    echo "$results"
}

# 从统一的JSON结果文件中获取类别的整体状态（基于测试结果）
get_category_overall_status() {
    local results_file="$1"
    local category="$2"
    
    local tests_json=$(get_test_results_from_json "$results_file" "$category")
    
    # 如果没有测试结果，返回未运行
    if [ "$tests_json" = "{}" ]; then
        echo "not_run"
        return
    fi
    
    # 检查是否有失败的测试
    local has_fail=$(echo "$tests_json" | jq -r 'to_entries[] | select(.value.result == "FAIL") | .key' 2>/dev/null)
    if [ -n "$has_fail" ]; then
        echo "FAIL"
        return
    fi
    
    # 检查是否有通过的测试
    local has_pass=$(echo "$tests_json" | jq -r 'to_entries[] | select(.value.result == "PASS") | .key' 2>/dev/null)
    if [ -n "$has_pass" ]; then
        echo "PASS"
        return
    fi
    
    # 默认为跳过
    echo "skip"
}

# =============================================================================
# HTML 报告生成
# =============================================================================

# 提取性能数据生成 JSON
extract_performance_data() {
    # 注意：TEST_SESSION_ID 已经在 session.sh 中导出，可以直接使用
    # 但是，如果我们在 reports.sh 中调用此函数，需要确保 RESULTS_DIR 等变量可用
    # 通常 SESSION_DIR 是可用的
    local perf_dir="${SESSION_DIR}/performance"
    
    local labels_json="[]"
    local throughput_json="[]"
    
    if [ -d "${perf_dir}" ]; then
        # 查找最新的结果文件
        local result_file=$(find "${perf_dir}" -name "performance_results_*.txt" | sort | tail -n 1)
        
        if [ -f "${result_file}" ]; then
            # 提取数据 - 使用 awk 提取数值，确保只取数字部分
            local seq_write_ops=$(grep -A 10 "=== 顺序写入 详细统计 ===" "${result_file}" | grep "吞吐量" | awk '{print $2}' | head -n 1)
            local rand_write_ops=$(grep -A 10 "=== 随机写入 详细统计 ===" "${result_file}" | grep "吞吐量" | awk '{print $2}' | head -n 1)
            local read_ops=$(grep -A 10 "=== 读取 详细统计 ===" "${result_file}" | grep "吞吐量" | awk '{print $2}' | head -n 1)
            
            # 混合工作负载可能有多个部分，需要更精确的匹配
            # 混合写入
            local mixed_write_ops=$(grep -A 10 "=== 混合工作负载 - 写入 详细统计 ===" "${result_file}" | grep "吞吐量" | awk '{print $2}' | head -n 1)
            # 混合读取
            local mixed_read_ops=$(grep -A 10 "=== 混合工作负载 - 读取 详细统计 ===" "${result_file}" | grep "吞吐量" | awk '{print $2}' | head -n 1)
            
            # 构建 JSON 数组字符串
            labels_json='["顺序写入", "随机写入", "读取", "混合写入", "混合读取"]'
            
            # 处理空值，默认为0
            seq_write_ops=${seq_write_ops:-0}
            rand_write_ops=${rand_write_ops:-0}
            read_ops=${read_ops:-0}
            mixed_write_ops=${mixed_write_ops:-0}
            mixed_read_ops=${mixed_read_ops:-0}
            
            throughput_json="[${seq_write_ops}, ${rand_write_ops}, ${read_ops}, ${mixed_write_ops}, ${mixed_read_ops}]"
        fi
    fi
    
    # 返回 JSON 对象
    echo "{\"labels\": ${labels_json}, \"throughput\": ${throughput_json}}" | tr -d '\n'
}

# 生成 HTML 报告
generate_html_report() {
    # 提取性能数据
    local perf_data=$(extract_performance_data)
    # 使用 jq 解析 JSON，如果 jq 不存在或解析失败，提供默认值
    # 注意：使用 tr -d '\n' 确保输出没有换行符，避免 sed 替换时出错
    local perf_labels=$(echo "$perf_data" | jq -c -r '.labels' 2>/dev/null | tr -d '\n')
    if [ -z "$perf_labels" ]; then perf_labels="[]"; fi
    
    local perf_throughput=$(echo "$perf_data" | jq -c -r '.throughput' 2>/dev/null | tr -d '\n')
    if [ -z "$perf_throughput" ]; then perf_throughput="[]"; fi

    # 确保 reports 目录存在
    local reports_dir="${SESSION_DIR}/reports"
    mkdir -p "${reports_dir}"
    
    local html_file="${reports_dir}/test_report_${TEST_SESSION_ID}.html"
    
    log_info "生成 HTML 报告: ${html_file}"
    
    cat > "${html_file}" << 'EOF'
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Java LSM Tree 测试报告</title>
    <!-- Chart.js CDN -->
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            line-height: 1.6;
            color: #333;
            background-color: #f5f5f5;
        }
        
        .container {
            max-width: 1200px;
            margin: 0 auto;
            padding: 20px;
        }
        
        .header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 30px;
            border-radius: 10px;
            margin-bottom: 30px;
            box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
        }
        
        .header h1 {
            font-size: 2.5em;
            margin-bottom: 10px;
        }
        
        .header .subtitle {
            font-size: 1.2em;
            opacity: 0.9;
        }
        
        .summary {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }
        
        .summary-card {
            background: white;
            padding: 25px;
            border-radius: 10px;
            box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
            text-align: center;
            transition: transform 0.3s ease;
        }
        
        .summary-card:hover {
            transform: translateY(-5px);
        }
        
        .summary-card h3 {
            color: #666;
            margin-bottom: 10px;
            font-size: 1.1em;
        }
        
        .summary-card .value {
            font-size: 2em;
            font-weight: bold;
            margin-bottom: 5px;
        }
        
        .summary-card .status {
            padding: 5px 15px;
            border-radius: 20px;
            font-size: 0.9em;
            font-weight: bold;
        }
        
        .status.pass {
            background-color: #d4edda;
            color: #155724;
        }
        
        .status.fail {
            background-color: #f8d7da;
            color: #721c24;
        }
        
        .status.skip {
            background-color: #fff3cd;
            color: #856404;
        }
        
        .test-section {
            background: white;
            margin-bottom: 30px;
            border-radius: 10px;
            box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
            overflow: hidden;
        }
        
        .test-section h2 {
            background: #f8f9fa;
            padding: 20px;
            margin: 0;
            border-bottom: 1px solid #dee2e6;
            color: #495057;
        }
        
        .test-content {
            padding: 20px;
        }
        
        .test-item {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 15px 0;
            border-bottom: 1px solid #eee;
        }
        
        .test-item:last-child {
            border-bottom: none;
        }
        
        .test-name {
            font-weight: 500;
        }
        
        .test-result {
            padding: 5px 15px;
            border-radius: 20px;
            font-size: 0.9em;
            font-weight: bold;
        }
        
        .performance-chart {
            margin: 20px 0;
            padding: 20px;
            background: #f8f9fa;
            border-radius: 5px;
            min-height: 400px;
            display: flex;
            flex-direction: column;
            align-items: center;
        }
        
        .chart-container {
            width: 100%;
            max-width: 800px;
            height: 350px;
        }
        
        .chart-placeholder {
            height: 200px;
            background: #e9ecef;
            border-radius: 5px;
            display: flex;
            align-items: center;
            justify-content: center;
            color: #6c757d;
            font-style: italic;
        }
        
        .footer {
            text-align: center;
            padding: 20px;
            color: #666;
            font-size: 0.9em;
        }
        
        .details-toggle {
            cursor: pointer;
            color: #007bff;
            text-decoration: underline;
            font-size: 0.9em;
        }
        
        .details {
            display: none;
            margin-top: 10px;
            padding: 15px;
            background: #f8f9fa;
            border-radius: 5px;
            font-family: monospace;
            font-size: 0.9em;
            white-space: pre-wrap;
        }
        
        .timestamp {
            color: #666;
            font-size: 0.9em;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>Java LSM Tree 测试报告</h1>
            <div class="subtitle">会话 ID: SESSION_ID_PLACEHOLDER</div>
            <div class="timestamp">生成时间: TIMESTAMP_PLACEHOLDER</div>
        </div>
        <div class="test-section">
            <h2>🛠️ 环境配置</h2>
            <div class="test-content">
                ENV_SECTION_PLACEHOLDER
            </div>
        </div>
        <div class="test-section">
            <h2>🔎 性能瓶颈分析与建议</h2>
            <div class="test-content">
                BOTTLENECKS_PLACEHOLDER
            </div>
        </div>
        
        <div class="summary">
            <div class="summary-card">
                <h3>总体状态</h3>
                <div class="value">OVERALL_STATUS_PLACEHOLDER</div>
                <div class="status OVERALL_STATUS_CLASS_PLACEHOLDER">OVERALL_STATUS_TEXT_PLACEHOLDER</div>
            </div>
            <div class="summary-card">
                <h3>测试总数</h3>
                <div class="value">TOTAL_TESTS_PLACEHOLDER</div>
                <div class="status">测试项目</div>
            </div>
            <div class="summary-card">
                <h3>通过率</h3>
                <div class="value">PASS_RATE_PLACEHOLDER%</div>
                <div class="status pass">成功率</div>
            </div>
            <div class="summary-card">
                <h3>执行时间</h3>
                <div class="value">DURATION_PLACEHOLDER</div>
                <div class="status">总耗时</div>
            </div>
        </div>
        <div class="test-section">
            <h2>📈 覆盖率</h2>
            <div class="test-content">
                COVERAGE_SECTION_PLACEHOLDER
            </div>
        </div>

        <div class="test-section">
            <h2>🧩 单元测试</h2>
            <div class="test-content">
                UNIT_TESTS_PLACEHOLDER
            </div>
        </div>
        
        <div class="test-section">
            <h2>🔧 工具与 CLI</h2>
            <div class="test-content">
                TOOLS_TESTS_PLACEHOLDER
            </div>
        </div>

        <div class="test-section">
            <h2>🧪 功能测试</h2>
            <div class="test-content">
                FUNCTIONAL_TESTS_PLACEHOLDER
            </div>
        </div>
        
        <div class="test-section">
            <h2>⚡ 性能测试</h2>
            <div class="test-content">
                PERFORMANCE_TESTS_PLACEHOLDER
                <div class="performance-chart">
                    <div class="chart-container">
                        <canvas id="throughputChart"></canvas>
                    </div>
                    <div style="margin-top: 20px; text-align: left; width: 100%; max-width: 800px; color: #666; font-size: 0.9em; padding: 15px; background: #fff; border-radius: 5px;">
                        <h4 style="margin-bottom: 10px; color: #333;">📊 指标说明：</h4>
                        <ul style="list-style-type: none; padding: 0;">
                            <li style="margin-bottom: 5px;">🔹 <strong>顺序写入</strong>: Key 按顺序递增写入。这是 LSM Tree 的理想场景，通常能达到最高吞吐量。</li>
                            <li style="margin-bottom: 5px;">🔹 <strong>随机写入</strong>: Key 随机分布写入。LSM Tree 将随机写转换为内存顺序写，性能依然很高，是核心优势之一。</li>
                            <li style="margin-bottom: 5px;">🔹 <strong>读取</strong>: 随机 Key 读取。在本基准测试中，我们增加了数据量并降低了 MemTable 阈值，强制触发 Flush 生成 SSTable。因此，读取操作需要查询 MemTable 和磁盘上的 SSTable，受磁盘 I/O 和层级结构影响，性能通常低于写入。</li>
                            <li style="margin-bottom: 5px;">🔹 <strong>混合写入/读取</strong>: 读写混合场景下的性能表现，反映真实业务负载下的综合能力。</li>
                        </ul>
                        <p style="margin-top: 10px; font-style: italic;">* 单位：ops/sec (每秒操作次数)，数值越高越好。</p>
                    </div>
                </div>
            </div>
        </div>
        
        <div class="test-section">
            <h2>💾 内存测试</h2>
            <div class="test-content">
                MEMORY_TESTS_PLACEHOLDER
            </div>
        </div>
        
        <div class="test-section">
            <h2>🔥 压力测试</h2>
            <div class="test-content">
                STRESS_TESTS_PLACEHOLDER
            </div>
        </div>
        
        <div class="footer">
            <p>报告由 Java LSM Tree 测试套件自动生成</p>
            <p>项目版本: PROJECT_VERSION_PLACEHOLDER</p>
        </div>
    </div>
    
    <script>
        // 性能图表数据
        const perfLabels = PERF_LABELS_PLACEHOLDER;
        const perfThroughput = PERF_THROUGHPUT_PLACEHOLDER;
        
        // 渲染图表
        if (perfLabels.length > 0 && perfThroughput.length > 0 && document.getElementById('throughputChart')) {
            const ctx = document.getElementById('throughputChart').getContext('2d');
            new Chart(ctx, {
                type: 'bar',
                data: {
                    labels: perfLabels,
                    datasets: [{
                        label: '吞吐量 (ops/sec)',
                        data: perfThroughput,
                        backgroundColor: [
                            'rgba(255, 99, 132, 0.5)',
                            'rgba(54, 162, 235, 0.5)',
                            'rgba(255, 206, 86, 0.5)',
                            'rgba(75, 192, 192, 0.5)',
                            'rgba(153, 102, 255, 0.5)'
                        ],
                        borderColor: [
                            'rgba(255, 99, 132, 1)',
                            'rgba(54, 162, 235, 1)',
                            'rgba(255, 206, 86, 1)',
                            'rgba(75, 192, 192, 1)',
                            'rgba(153, 102, 255, 1)'
                        ],
                        borderWidth: 1
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        title: {
                            display: true,
                            text: '基准测试吞吐量对比 (越高越好)'
                        },
                        legend: {
                            display: false
                        }
                    },
                    scales: {
                        y: {
                            beginAtZero: true,
                            title: {
                                display: true,
                                text: 'ops/sec'
                            }
                        }
                    }
                }
            });
        }
        
        // 切换详细信息显示
        function toggleDetails(element) {
            const details = element.nextElementSibling;
            if (details.style.display === 'none' || details.style.display === '') {
                details.style.display = 'block';
                element.textContent = '隐藏详细信息';
            } else {
                details.style.display = 'none';
                element.textContent = '显示详细信息';
            }
        }
        
        // 为所有详细信息切换按钮添加事件监听器
        document.addEventListener('DOMContentLoaded', function() {
            const toggles = document.querySelectorAll('.details-toggle');
            toggles.forEach(toggle => {
                toggle.addEventListener('click', function() {
                    toggleDetails(this);
                });
            });
        });
    </script>
</body>
</html>
EOF
    
    # 替换性能数据占位符
    sed -i '' "s/PERF_LABELS_PLACEHOLDER/${perf_labels}/g" "${html_file}"
    sed -i '' "s/PERF_THROUGHPUT_PLACEHOLDER/${perf_throughput}/g" "${html_file}"
    
    # 替换 HTML 报告中的占位符
    replace_html_placeholders "${html_file}"
    
    log_success "HTML 报告生成完成: ${html_file}"
}

# 替换 HTML 报告中的占位符
replace_html_placeholders() {
    local html_file="$1"
    
    # 基本信息
    sed -i '' "s/SESSION_ID_PLACEHOLDER/${TEST_SESSION_ID}/g" "${html_file}"
    sed -i '' "s/TIMESTAMP_PLACEHOLDER/$(date)/g" "${html_file}"
    sed -i '' "s/PROJECT_VERSION_PLACEHOLDER/${PROJECT_VERSION}/g" "${html_file}"
    
    # 计算测试统计
    local total_tests=0
    local passed_tests=0
    local failed_tests=0
    local overall_status="UNKNOWN"
    local overall_status_class="skip"
    local overall_status_text="未知"
    
    # 统计各类测试结果 - 使用统一的JSON结果文件
    local results_file="${SESSION_DIR}/test_results.json"
    for test_type in unit tools functional performance memory stress; do
        local category_status=$(get_category_status_from_json "$results_file" "$test_type")
        if [ "$category_status" != "not_run" ] && [ "$category_status" != "pending" ]; then
            total_tests=$((total_tests + 1))
            local overall_status=$(get_category_overall_status "$results_file" "$test_type")
            if [ "$overall_status" = "PASS" ] || [ "$overall_status" = "pass" ]; then
                passed_tests=$((passed_tests + 1))
            elif [ "$overall_status" = "FAIL" ] || [ "$overall_status" = "fail" ]; then
                failed_tests=$((failed_tests + 1))
            fi
        fi
    done
    
    # 计算通过率
    local pass_rate=0
    if [ ${total_tests} -gt 0 ]; then
        pass_rate=$((passed_tests * 100 / total_tests))
    fi
    
    # 确定总体状态
    if [ ${failed_tests} -eq 0 ] && [ ${total_tests} -gt 0 ]; then
        overall_status="✅"
        overall_status_class="pass"
        overall_status_text="全部通过"
    elif [ ${failed_tests} -gt 0 ]; then
        overall_status="❌"
        overall_status_class="fail"
        overall_status_text="部分失败"
    fi
    
    # 计算执行时间
    local duration="未知"
    local results_file="${SESSION_DIR}/test_results.json"
    
    if [ -f "${results_file}" ]; then
        # 尝试从 JSON 读取 start_time
        local start_time_iso=$(jq -r '.session_info.start_time // empty' "${results_file}" 2>/dev/null)
        
        # DEBUG
        # echo "DEBUG: start_time_iso=${start_time_iso}" >> "${SESSION_DIR}/debug_time.log"
        
        if [ -n "${start_time_iso}" ]; then
            local start_ts=0
            local current_ts=$(date +%s)
            
            # 尝试转换为时间戳 (兼容 GNU date 和 BSD date)
            if date --version >/dev/null 2>&1; then
                # GNU date
                start_ts=$(date -d "${start_time_iso}" +%s 2>/dev/null)
            else
                # BSD date (macOS) - 尝试解析 ISO 8601 格式
                # 注意：ISO 8601 格式 2026-03-02T16:30:05+08:00
                # 如果不带秒偏移量，尝试解析
                start_ts=$(date -j -f "%Y-%m-%dT%H:%M:%SZ" "${start_time_iso}" +%s 2>/dev/null)
                if [ -z "${start_ts}" ]; then
                    # 尝试带时区偏移量的格式 (如 +08:00)
                    # BSD date 的 %z 需要去掉冒号，如 +0800，而 ISO 8601 是 +08:00
                    local clean_iso=$(echo "${start_time_iso}" | sed 's/\([-+][0-9][0-9]\):\([0-9][0-9]\)$/\1\2/')
                    start_ts=$(date -j -f "%Y-%m-%dT%H:%M:%S%z" "${clean_iso}" +%s 2>/dev/null)
                fi
                if [ -z "${start_ts}" ]; then
                    # 尝试其他常见格式
                    start_ts=$(date -j -f "%Y-%m-%d %H:%M:%S" "${start_time_iso}" +%s 2>/dev/null)
                fi
            fi
            
            # DEBUG
            # echo "DEBUG: start_ts=${start_ts}, current_ts=${current_ts}" >> "${SESSION_DIR}/debug_time.log"
            
            if [ -n "${start_ts}" ] && [ "${start_ts}" -gt 0 ]; then
                local elapsed=$((current_ts - start_ts))
                # 修正时区问题：如果时间差接近8小时（28800秒），可能是时区解析错误
                # 如果误差在 8小时 +/- 10分钟内，且测试明显不应该跑那么久，尝试修正
                if [ ${elapsed} -ge 28200 ] && [ ${elapsed} -le 29400 ]; then
                     # echo "DEBUG: Detecting timezone offset issue (8h), correcting..." >> "${SESSION_DIR}/debug_time.log"
                     elapsed=$((elapsed - 28800))
                fi
                
                if [ ${elapsed} -lt 0 ]; then elapsed=0; fi
                
                # 格式化显示时间
                if [ ${elapsed} -ge 3600 ]; then
                    local hours=$((elapsed / 3600))
                    local minutes=$(( (elapsed % 3600) / 60 ))
                    local seconds=$((elapsed % 60))
                    duration="${hours}小时${minutes}分${seconds}秒"
                elif [ ${elapsed} -ge 60 ]; then
                    local minutes=$((elapsed / 60))
                    local seconds=$((elapsed % 60))
                    duration="${minutes}分${seconds}秒"
                else
                    duration="${elapsed}秒"
                fi
            fi
        fi
    fi
    
    # 替换统计信息
    sed -i '' "s/OVERALL_STATUS_PLACEHOLDER/${overall_status}/g" "${html_file}"
    sed -i '' "s/OVERALL_STATUS_CLASS_PLACEHOLDER/${overall_status_class}/g" "${html_file}"
    sed -i '' "s/OVERALL_STATUS_TEXT_PLACEHOLDER/${overall_status_text}/g" "${html_file}"
    sed -i '' "s/TOTAL_TESTS_PLACEHOLDER/${total_tests}/g" "${html_file}"
    sed -i '' "s/PASS_RATE_PLACEHOLDER/${pass_rate}/g" "${html_file}"
    sed -i '' "s/DURATION_PLACEHOLDER/${duration}/g" "${html_file}"
    
    # 生成各类测试的详细内容
    generate_test_section_html "unit" "单元测试" "${html_file}"
    generate_test_section_html "tools" "工具与 CLI" "${html_file}"
    generate_test_section_html "functional" "功能测试" "${html_file}"
    generate_test_section_html "performance" "性能测试" "${html_file}"
    generate_test_section_html "memory" "内存测试" "${html_file}"
    generate_test_section_html "stress" "压力测试" "${html_file}"
    generate_coverage_section_html "${html_file}"
    generate_env_section_html "${html_file}"
    generate_bottlenecks_section_html "${html_file}"
}

# 生成测试部分的 HTML 内容 - 使用统一JSON格式
generate_test_section_html() {
    local test_type="$1"
    local test_name="$2"
    local html_file="$3"
    local placeholder="$(echo "${test_type}" | tr '[:lower:]' '[:upper:]')_TESTS_PLACEHOLDER"
    
    local results_file="${SESSION_DIR}/test_results.json"
    local test_dir="${SESSION_DIR}/${test_type}"
    local content=""
    
    # 获取类别状态
    local category_status=$(get_category_status_from_json "$results_file" "$test_type")
    local overall_status=$(get_category_overall_status "$results_file" "$test_type")
    
    local status="未运行"
    local status_class="skip"
    
    case "${category_status}" in
        "completed")
            case "${overall_status}" in
                "pass")
                    status="已完成"
                    status_class="pass"
                    ;;
                "fail")
                    status="部分失败"
                    status_class="fail"
                    ;;
                *)
                    status="已完成"
                    status_class="skip"
                    ;;
            esac
            ;;
        "running")
            status="运行中"
            status_class="skip"
            ;;
        "failed")
            status="失败"
            status_class="fail"
            ;;
        *)
            status="未运行"
            status_class="skip"
            ;;
    esac
    
    content="<div class=\"test-item\">
                <div class=\"test-name\">${test_name}</div>
                <div class=\"test-result ${status_class}\">${status}</div>
            </div>"
    
    # 添加详细的测试结果
    local tests_json=$(get_test_results_from_json "$results_file" "$test_type")
    if [ "$tests_json" != "{}" ]; then
        local details=""
        # 使用jq解析测试结果
        while IFS= read -r line; do
            if [ -n "$line" ]; then
                local test_name_detail=$(echo "$line" | cut -d':' -f1 | tr -d '"')
                local test_result=$(echo "$line" | cut -d':' -f2 | tr -d '" ')
                local result_class="skip"
                case "${test_result}" in
                    "PASS") result_class="pass" ;;
                    "FAIL") result_class="fail" ;;
                    "SKIP") result_class="skip" ;;
                esac
                details="${details}<div class=\"test-item\">
                            <div class=\"test-name\">  ${test_name_detail}</div>
                            <div class=\"test-result ${result_class}\">${test_result}</div>
                        </div>"
            fi
        done < <(echo "$tests_json" | jq -r 'to_entries[] | "\(.key):\(.value.result)"' 2>/dev/null)
        
        if [ -n "${details}" ]; then
            content="${content}${details}"
        fi
    fi
    
    # 添加日志文件链接
    if [ -d "${test_dir}" ] && ls "${test_dir}"/*.log >/dev/null 2>&1; then
        content="${content}<div class=\"test-item\">
                    <div class=\"details-toggle\" onclick=\"toggleDetails(this)\">显示详细信息</div>
                    <div class=\"details\">日志文件位置: ${test_dir}/</div>
                </div>"
    fi
    
    # 使用临时文件处理多行内容替换
    local temp_file=$(mktemp)
    local temp_content=$(mktemp)
    
    # 将内容写入临时文件，转义特殊字符
    echo "${content}" | sed 's/[[\.*^$()+?{|]/\\&/g' > "${temp_content}"
    
    # 使用awk进行替换，避免sed的多行问题
    awk -v placeholder="${placeholder}" -v content_file="${temp_content}" '
    {
        if ($0 ~ placeholder) {
            while ((getline line < content_file) > 0) {
                print line
            }
            close(content_file)
        } else {
            print $0
        }
    }' "${html_file}" > "${temp_file}"
    
    mv "${temp_file}" "${html_file}"
    rm -f "${temp_content}"
}

generate_coverage_section_html() {
    local html_file="$1"
    local coverage_file="${SESSION_DIR}/coverage_summary.json"
    local content="<div>未找到覆盖率数据</div>"
    if [ -f "${coverage_file}" ]; then
        local overall=$(jq -r '.overall_line' "${coverage_file}" 2>/dev/null)
        local rows=""
        while IFS= read -r kv; do
            local k=$(echo "$kv" | cut -d: -f1)
            local v=$(echo "$kv" | cut -d: -f2)
            local pct=$(awk -v x="$v" 'BEGIN{printf "%0.1f", x*100}')
            rows="${rows}<tr><td>${k}</td><td>${pct}%</td></tr>"
        done < <(jq -r '.core_line | to_entries[] | "\(.key):\(.value)"' "${coverage_file}" 2>/dev/null)
        local overall_pct=$(awk -v x="$overall" 'BEGIN{printf "%0.1f", x*100}')
        content="<div>总体行覆盖率：${overall_pct}%</div><table style=\"width:100%;margin-top:10px;border-collapse:collapse;\"><thead><tr><th style=\"text-align:left;border-bottom:1px solid #eee;\">模块</th><th style=\"text-align:left;border-bottom:1px solid #eee;\">行覆盖率</th></tr></thead><tbody>${rows}</tbody></table>"
    fi
    local tmp=$(mktemp)
    sed "s/COVERAGE_SECTION_PLACEHOLDER/$(printf '%s' "${content}" | sed 's/[\&/]/\\&/g')/" "${html_file}" > "${tmp}"
    mv "${tmp}" "${html_file}"
}

generate_env_section_html() {
    local html_file="$1"
    local json_file="${SESSION_DIR}/reports/test_report_${TEST_SESSION_ID}.json"
    local content=""
    if [ -f "${json_file}" ]; then
        local java=$(jq -r '.system_info.java_version' "$json_file" 2>/dev/null)
        local os=$(jq -r '.system_info.os_type' "$json_file" 2>/dev/null)
        local osv=$(jq -r '.system_info.os_version' "$json_file" 2>/dev/null)
        local cpu=$(jq -r '.system_info.cpu_cores' "$json_file" 2>/dev/null)
        local mem=$(jq -r '.system_info.memory_gb' "$json_file" 2>/dev/null)
        content="<ul><li>Java: ${java}</li><li>OS: ${os} ${osv}</li><li>CPU Cores: ${cpu}</li><li>Memory: ${mem} GB</li></ul>"
    else
        content="<div>未找到系统信息</div>"
    fi
    local tmp=$(mktemp)
    sed "s/ENV_SECTION_PLACEHOLDER/$(printf '%s' "${content}" | sed 's/[\&/]/\\&/g')/" "${html_file}" > "${tmp}"
    mv "${tmp}" "${html_file}"
}

generate_bottlenecks_section_html() {
    local html_file="$1"
    local content="<ul><li>写入放大与刷盘：优化 MemTable 阈值与合并策略</li><li>读放大：启用布隆过滤器与合理的分区策略</li><li>GC 影响：观察 GC 日志，调整堆大小与收集器</li><li>并发竞争：根据 CPU 调整线程数，避免过度上下文切换</li></ul>"
    local tmp=$(mktemp)
    sed "s/BOTTLENECKS_PLACEHOLDER/$(printf '%s' "${content}" | sed 's/[\&/]/\\&/g')/" "${html_file}" > "${tmp}"
    mv "${tmp}" "${html_file}"
}

# =============================================================================
# JSON 报告生成
# =============================================================================

# 生成 JSON 报告
generate_json_report() {
    # 确保 reports 目录存在
    local reports_dir="${SESSION_DIR}/reports"
    mkdir -p "${reports_dir}"
    
    local json_file="${reports_dir}/test_report_${TEST_SESSION_ID}.json"
    
    log_info "生成 JSON 报告: ${json_file}"
    
    # 收集测试数据
    local session_info=$(collect_session_info)
    local test_results=$(collect_test_results)
    local system_info=$(collect_system_info)
    
    # 生成 JSON 结构
    cat > "${json_file}" << EOF
{
  "report_metadata": {
    "session_id": "${TEST_SESSION_ID}",
    "generated_at": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
    "report_version": "1.0",
    "generator": "Java LSM Tree Test Suite"
  },
  "session_info": ${session_info},
  "system_info": ${system_info},
  "test_results": ${test_results},
  "summary": $(generate_test_summary_json)
}
EOF
    
    log_success "JSON 报告生成完成: ${json_file}"
}

# 收集会话信息
collect_session_info() {
    local results_file="${SESSION_DIR}/test_results.json"
    local start_time="null"
    local end_time="null"
    local status="unknown"
    
    if [ -f "${results_file}" ]; then
        start_time=$(jq -r '.session_info.start_time // "null"' "${results_file}")
        end_time=$(jq -r '.session_info.end_time // "null"' "${results_file}")
        status=$(jq -r '.session_info.status // "unknown"' "${results_file}")
    fi
    
    # 格式化时间为 JSON 字符串
    local start_time_json="null"
    if [ "${start_time}" != "null" ] && [ -n "${start_time}" ]; then
        start_time_json="\"${start_time}\""
    fi
    
    local end_time_json="null"
    if [ "${end_time}" != "null" ] && [ -n "${end_time}" ]; then
        end_time_json="\"${end_time}\""
    fi
    
    # 计算 duration
    local duration=0
    if [ "${start_time}" != "null" ] && [ -n "${start_time}" ]; then
        local start_ts=0
        local end_ts=$(date +%s)
        
        # 解析开始时间
         if date --version >/dev/null 2>&1; then
             start_ts=$(date -d "${start_time}" +%s 2>/dev/null)
         else
             start_ts=$(date -j -f "%Y-%m-%dT%H:%M:%SZ" "${start_time}" +%s 2>/dev/null)
             if [ -z "${start_ts}" ]; then
                 local clean_iso=$(echo "${start_time}" | sed 's/\([-+][0-9][0-9]\):\([0-9][0-9]\)$/\1\2/')
                 start_ts=$(date -j -f "%Y-%m-%dT%H:%M:%S%z" "${clean_iso}" +%s 2>/dev/null)
             fi
             if [ -z "${start_ts}" ]; then
                 start_ts=$(date -j -f "%Y-%m-%d %H:%M:%S" "${start_time}" +%s 2>/dev/null)
             fi
         fi
         
         # 解析结束时间（如果有）
         if [ "${end_time}" != "null" ] && [ -n "${end_time}" ]; then
             if date --version >/dev/null 2>&1; then
                 end_ts=$(date -d "${end_time}" +%s 2>/dev/null)
             else
                 end_ts=$(date -j -f "%Y-%m-%dT%H:%M:%SZ" "${end_time}" +%s 2>/dev/null)
                 if [ -z "${end_ts}" ]; then
                      local clean_iso=$(echo "${end_time}" | sed 's/\([-+][0-9][0-9]\):\([0-9][0-9]\)$/\1\2/')
                      end_ts=$(date -j -f "%Y-%m-%dT%H:%M:%S%z" "${clean_iso}" +%s 2>/dev/null)
                  fi
                 if [ -z "${end_ts}" ]; then
                     end_ts=$(date -j -f "%Y-%m-%d %H:%M:%S" "${end_time}" +%s 2>/dev/null)
                 fi
             fi
         fi
        
        if [ -n "${start_ts}" ] && [ "${start_ts}" -gt 0 ] && [ -n "${end_ts}" ]; then
            duration=$((end_ts - start_ts))
            if [ ${duration} -lt 0 ]; then duration=0; fi
        fi
    fi

    cat << EOF
{
  "session_id": "${TEST_SESSION_ID}",
  "start_time": ${start_time_json},
  "end_time": ${end_time_json},
  "status": "${status}",
  "duration_seconds": ${duration}
}
EOF
}

# 收集系统信息
collect_system_info() {
    cat << EOF
{
  "java_version": "$(java -version 2>&1 | head -n 1 | sed 's/.*"\(.*\)".*/\1/')",
  "java_opts": "${JAVA_OPTS}",
  "project_version": "${PROJECT_VERSION}",
  "project_artifact": "${PROJECT_ARTIFACT}",
  "os_type": "$(uname -s)",
  "os_version": "$(uname -r)",
  "hostname": "$(hostname)",
  "cpu_cores": $(sysctl -n hw.ncpu 2>/dev/null || echo "null"),
  "memory_gb": $(echo "scale=2; $(sysctl -n hw.memsize 2>/dev/null || echo "0") / 1024 / 1024 / 1024" | bc 2>/dev/null || echo "null")
}
EOF
}

# 收集测试结果
collect_test_results() {
    local results="{"
    local first=true
    
    for test_type in unit tools functional performance memory stress; do
        if [ "${first}" = false ]; then
            results="${results},"
        fi
        first=false
        
        results="${results}\"${test_type}\": $(collect_test_type_results "${test_type}")"
    done
    
    results="${results}}"
    echo "${results}"
}

# 收集特定类型测试的结果
collect_test_type_results() {
    local test_type="$1"
    local test_dir="${SESSION_DIR}/${test_type}"
    local test_results_file="${SESSION_DIR}/test_results.json"
    
    local status="not_run"
    local start_time=""
    local end_time=""
    local results="{}"
    local logs="[]"
    
    if [ -d "${test_dir}" ]; then
        # 从统一的JSON文件读取状态和结果
        if [ -f "${test_results_file}" ]; then
            status=$(get_category_status_from_json "${test_results_file}" "${test_type}")
            
            # 获取开始和结束时间
            start_time=$(jq -r ".categories.${test_type}.start_time // null" "${test_results_file}" 2>/dev/null)
            end_time=$(jq -r ".categories.${test_type}.end_time // null" "${test_results_file}" 2>/dev/null)
            
            # 获取测试结果
            local test_results=$(jq -r ".categories.${test_type}.tests // {}" "${test_results_file}" 2>/dev/null)
            if [ "${test_results}" != "null" ] && [ "${test_results}" != "{}" ]; then
                results="${test_results}"
            fi
        fi
        
        # 收集日志文件
        local log_files=()
        for log_file in "${test_dir}"/*.log; do
            if [ -f "${log_file}" ]; then
                local filename=$(basename "${log_file}")
                log_files+=("\"${filename}\"")
            fi
        done
        
        if [ ${#log_files[@]} -gt 0 ]; then
            logs="[$(IFS=','; echo "${log_files[*]}")]"
        fi
    fi
    
    # 确保时间字段正确格式化
    local formatted_start_time="null"
    local formatted_end_time="null"
    
    if [ "${start_time}" != "null" ] && [ -n "${start_time}" ]; then
        formatted_start_time="\"${start_time}\""
    fi
    
    if [ "${end_time}" != "null" ] && [ -n "${end_time}" ]; then
        formatted_end_time="\"${end_time}\""
    fi
    
    cat << EOF
{
  "status": "${status}",
  "start_time": ${formatted_start_time},
  "end_time": ${formatted_end_time},
  "results": ${results},
  "log_files": ${logs},
  "test_directory": "${test_dir}"
}
EOF
}

# 生成测试总结 JSON
generate_test_summary_json() {
    local test_results_file="${SESSION_DIR}/test_results.json"
    local total_tests=0
    local passed_tests=0
    local failed_tests=0
    local skipped_tests=0
    
    if [ -f "${test_results_file}" ]; then
    for test_type in unit tools functional performance memory stress; do
            local status=$(get_category_status_from_json "${test_results_file}" "${test_type}")
            local overall_status=$(get_category_overall_status "${test_results_file}" "${test_type}")
            
            total_tests=$((total_tests + 1))
            case "${overall_status}" in
                "PASS") passed_tests=$((passed_tests + 1)) ;;
                "FAIL") failed_tests=$((failed_tests + 1)) ;;
                *) skipped_tests=$((skipped_tests + 1)) ;;
            esac
        done
    else
        # 如果没有统一的JSON文件，回退到原来的逻辑
        for test_type in functional performance memory stress; do
            local status_file="${SESSION_DIR}/${test_type}/test_status.txt"
            if [ -f "${status_file}" ]; then
                local status=$(cat "${status_file}")
                total_tests=$((total_tests + 1))
                case "${status}" in
                    "completed") passed_tests=$((passed_tests + 1)) ;;
                    "failed") failed_tests=$((failed_tests + 1)) ;;
                    *) skipped_tests=$((skipped_tests + 1)) ;;
                esac
            else
                total_tests=$((total_tests + 1))
                skipped_tests=$((skipped_tests + 1))
            fi
        done
    fi
    
    local pass_rate=0
    if [ ${total_tests} -gt 0 ]; then
        pass_rate=$((passed_tests * 100 / total_tests))
    fi
    
    local overall_status="unknown"
    if [ ${failed_tests} -eq 0 ] && [ ${passed_tests} -gt 0 ]; then
        overall_status="pass"
    elif [ ${failed_tests} -gt 0 ]; then
        overall_status="fail"
    fi
    
    cat << EOF
{
  "total_tests": ${total_tests},
  "passed_tests": ${passed_tests},
  "failed_tests": ${failed_tests},
  "skipped_tests": ${skipped_tests},
  "pass_rate_percent": ${pass_rate},
  "overall_status": "${overall_status}"
}
EOF
}

# =============================================================================
# 报告生成主函数
# =============================================================================

# 生成所有格式的报告
generate_all_reports() {
    log_info "开始生成测试报告..."
    
    # 确保会话目录存在
    if [ ! -d "${SESSION_DIR}" ]; then
        log_error "会话目录不存在: ${SESSION_DIR}"
        return 1
    fi
    
    # 生成 HTML 报告
    if ! generate_html_report; then
        log_error "HTML 报告生成失败"
        return 1
    fi
    
    # 生成 JSON 报告
    if ! generate_json_report; then
        log_error "JSON 报告生成失败"
        return 1
    fi
    
    log_success "所有报告生成完成"
    log_info "报告位置: ${SESSION_DIR}/reports/"
    
    # 列出生成的报告文件
    ls -la "${SESSION_DIR}/reports"/test_report_* 2>/dev/null || true
}

# 生成指定格式的报告
generate_report_by_format() {
    local format="$1"
    
    case "${format}" in
        "html")
            generate_html_report
            ;;
        "json")
            generate_json_report
            ;;
        "all")
            generate_all_reports
            ;;
        *)
            log_error "未知的报告格式: ${format}"
            log_info "支持的格式: html, json, all"
            return 1
            ;;
    esac
}
