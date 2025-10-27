# WAL 文件分析工具使用指南

## 1. 概述

WAL（Write-Ahead Log）文件分析工具是 LSM Tree 存储引擎的专业调试和运维工具，基于 `com.brianxiadong.lsmtree.tools.WALAnalyzer` 核心类实现。该工具遵循 WAL-first 原则，提供预写日志文件的深度分析、完整性验证和性能诊断功能，支持崩溃恢复场景下的数据一致性检查。

---

## 2. 功能特性

### 2.1 核心分析功能

- **格式验证**: 基于 `LogEntry.fromString()` 解析器验证 WAL 条目格式完整性
- **操作统计**: 统计 PUT/DELETE 操作分布、键频率分析和时间跨度计算
- **数据完整性检查**: 检测损坏的日志条目和格式异常
- **JSON 导出**: 结构化导出分析结果，支持后续数据处理管道
- **交互式分析**: 基于 `WALAnalyzerCLI` 的命令行交互界面

### 2.2 高级分析功能

- **时序分析**: 基于时间戳的操作序列分析和性能热点识别
- **键空间分析**: 唯一键统计、键访问频率分布和热点键识别
- **操作模式识别**: PUT/DELETE 比例分析，支持写入模式优化建议
- **文件元数据分析**: 文件大小、修改时间和存储效率评估

---

## 3. 安装和编译

确保项目已正确编译：

```bash
./build.sh
```

---

## 4. WAL 文件格式规范

### 4.1 格式定义

WAL 文件采用行分隔的文本格式，基于 `WriteAheadLog.LogEntry.toString()` 方法序列化。每行表示一个原子操作，格式严格遵循以下规范：

```text
<OPERATION>|<KEY>|<VALUE>|<TIMESTAMP>
```

### 4.2 字段规范

| 字段          | 类型                      | 描述         | 约束                                                  |
| ------------- | ------------------------- | ------------ | ----------------------------------------------------- |
| **OPERATION** | `WriteAheadLog.Operation` | 操作类型枚举 | `PUT` 或 `DELETE`                                     |
| **KEY**       | `String`                  | 键标识符     | 非空字符串，支持 UTF-8 编码                           |
| **VALUE**     | `String`                  | 数据值       | PUT 操作必填，DELETE 操作为空字符串                   |
| **TIMESTAMP** | `long`                    | 操作时间戳   | Unix 毫秒时间戳，由 `System.currentTimeMillis()` 生成 |

### 4.3 格式示例

```text
PUT|user:1001|{"name":"Alice","email":"alice@example.com","created":1698123456789}|1698123456789
PUT|session:abc123|{"userId":1001,"loginTime":1698123456790,"ip":"192.168.1.100"}|1698123456790
DELETE|user:1001||1698123456791
PUT|config:timeout|30000|1698123456792
```

### 4.4 解析机制

解析器 `LogEntry.fromString()` 使用管道符（`|`）作为字段分隔符，支持以下特性：

- **容错处理**: 自动跳过格式错误的行
- **时间戳兼容**: 缺失时间戳时使用当前时间
- **值处理**: DELETE 操作的空值自动标准化为 `null`

---

## 5. 使用方法

### 5.1 命令行接口

工具通过 `analyze-wal.sh` 脚本启动，内部调用 `WALAnalyzerCLI` 主类：

```bash
./analyze-wal.sh <COMMAND> [OPTIONS] [ARGUMENTS]
```

### 5.2 核心命令详解

#### 5.2.1 analyze - WAL 深度分析

执行完整的 WAL 文件分析，调用 `WALAnalyzer.analyzeWAL()` 方法：

```bash
# 基础分析模式
./analyze-wal.sh analyze <wal_file>

# 详细条目展示模式
./analyze-wal.sh analyze <wal_file> --show-entries
```

**分析报告结构：**

```text
============================================================
WAL文件分析报告
============================================================
文件路径: /data/lsm/wal_20231024_125736.log
文件大小: 2.1 KB (2,147 bytes)
最后修改: 2023-10-24 12:57:36
文件状态: 正常 (0 errors)

----------------------------------------
操作统计分析
----------------------------------------
总条目数: 156
PUT操作: 124 (79.49%)
DELETE操作: 32 (20.51%)
唯一键数: 89
键重复率: 42.95%
时间跨度: 1,247 ms
操作频率: 125.1 ops/sec
开始时间: 2023-10-24 12:57:36.789
结束时间: 2023-10-24 12:57:38.036

----------------------------------------
性能指标
----------------------------------------
平均条目大小: 13.8 bytes
存储效率: 87.3%
时序一致性: ✓ 单调递增
```

#### 5.2.2 validate - 格式完整性验证

基于 `WALAnalyzer.validateWAL()` 执行格式验证，检查每行的解析完整性：

```bash
./analyze-wal.sh validate <wal_file>
```

**验证输出：**

```text
✅ WAL文件验证通过: /data/lsm/wal_20231024_125736.log
   - 总条目: 156
   - 有效条目: 156 (100.00%)
   - 格式错误: 0
   - 验证耗时: 12ms
```

#### 5.2.3 export - 结构化数据导出

调用 `WALAnalyzer.exportToJson()` 将分析结果序列化为 JSON 格式：

```bash
./analyze-wal.sh export <wal_file> <output_file>
```

**JSON 输出结构：**

```json
{
  "metadata": {
    "analyzer_version": "1.0.0",
    "analysis_timestamp": 1698123456789,
    "file_info": {
      "path": "/data/lsm/wal_20231024_125736.log",
      "size_bytes": 2147,
      "last_modified": 1698123456000,
      "encoding": "UTF-8"
    }
  },
  "statistics": {
    "total_entries": 156,
    "put_operations": 124,
    "delete_operations": 32,
    "unique_keys": 89,
    "key_frequency": { "user:1001": 3, "session:abc123": 2 },
    "time_span_ms": 1247,
    "first_timestamp": 1698123456789,
    "last_timestamp": 1698123458036
  },
  "entries": [
    {
      "operation": "PUT",
      "key": "user:1001",
      "value": "{\"name\":\"Alice\",\"email\":\"alice@example.com\"}",
      "timestamp": 1698123456789,
      "entry_index": 0
    }
  ]
}
```

#### 5.2.4 interactive - 交互式分析会话

启动基于 `Scanner` 的交互式命令行界面：

```bash
./analyze-wal.sh interactive
```

**支持的交互命令：**

| 命令                              | 功能           | 示例                               |
| --------------------------------- | -------------- | ---------------------------------- |
| `analyze <file> [--show-entries]` | 执行文件分析   | `analyze /data/wal.log`            |
| `validate <file>`                 | 验证文件格式   | `validate /data/wal.log`           |
| `export <file> <output>`          | 导出 JSON 数据 | `export /data/wal.log result.json` |
| `help`                            | 显示命令帮助   | `help`                             |
| `exit` / `quit`                   | 退出交互模式   | `exit`                             |

#### 5.2.5 help - 命令帮助

显示完整的命令行使用说明：

```bash
./analyze-wal.sh help
```

### 5.3 输出格式说明

#### 5.3.1 控制台输出格式

基于 `WALAnalysisResult.toString()` 的标准化输出：

```text
WAL文件分析结果: /data/lsm/wal_20231024_125736.log
文件大小: 2,147 bytes (2.1 KB)
总条目数: 156
PUT操作: 124 (79.49%)
DELETE操作: 32 (20.51%)
唯一键数: 89
重复键数: 67
时间跨度: 1,247ms
首次时间戳: 1698123456789 (2023-10-24 12:57:36.789)
最后时间戳: 1698123458036 (2023-10-24 12:57:38.036)
平均操作频率: 125.0 ops/sec
```

#### 5.3.2 JSON 导出格式

基于 `WALAnalyzer.exportToJson()` 的结构化输出：

```json
{
  "metadata": {
    "analyzer_version": "1.0.0",
    "analysis_timestamp": 1698123460000,
    "file_info": {
      "path": "/data/lsm/wal_20231024_125736.log",
      "size_bytes": 2147,
      "last_modified": 1698123456000,
      "encoding": "UTF-8",
      "line_count": 156
    }
  },
  "statistics": {
    "total_entries": 156,
    "put_operations": 124,
    "delete_operations": 32,
    "unique_keys": 89,
    "duplicate_keys": 67,
    "key_frequency": {
      "user:1001": 3,
      "session:abc123": 2,
      "config:timeout": 1
    },
    "time_span_ms": 1247,
    "first_timestamp": 1698123456789,
    "last_timestamp": 1698123458036,
    "avg_ops_per_second": 125.0
  },
  "entries": [
    {
      "operation": "PUT",
      "key": "user:1001",
      "value": "{\"name\":\"Alice\",\"email\":\"alice@example.com\"}",
      "timestamp": 1698123456789,
      "entry_index": 0,
      "line_number": 1
    }
  ]
}
```

#### 5.3.3 条目详情

当使用 `--show-entries` 选项时，会显示每个日志条目的详细信息：

- **操作**：PUT 或 DELETE
- **键**：操作的键
- **值**：操作的值（DELETE 操作显示为空）
- **时间戳**：操作的时间戳（格式化为可读时间）

---

## 6. 应用场景

### 6.1 崩溃恢复诊断

在 LSM Tree 崩溃恢复场景中，验证 WAL 文件的完整性和一致性：

```bash
# 检查 WAL 文件格式完整性
./analyze-wal.sh validate /data/lsm/wal_crash_20231024.log

# 分析崩溃前的操作序列
./analyze-wal.sh analyze /data/lsm/wal_crash_20231024.log --show-entries

# 导出崩溃分析报告
./analyze-wal.sh export /data/lsm/wal_crash_20231024.log crash_analysis.json
```

**诊断要点：**

- 验证时间戳单调性，检测时钟回退问题
- 分析最后几个操作的完整性
- 检查是否存在截断的日志条目

### 6.2 性能基准测试

分析 WAL 写入性能和操作模式，支持性能调优：

```bash
# 分析高负载期间的 WAL 性能
./analyze-wal.sh analyze /data/lsm/wal_benchmark_20231024.log

# 导出性能数据用于可视化分析
./analyze-wal.sh export /data/lsm/wal_benchmark_20231024.log perf_analysis.json
```

**性能指标：**

- 操作频率（ops/sec）
- PUT/DELETE 比例分析
- 键访问热点识别
- 时间跨度和吞吐量评估

### 6.3 数据合规审计

基于 WAL 记录进行数据操作审计和合规性检查：

```bash
# 审计特定时间窗口的数据操作
./analyze-wal.sh export /data/lsm/wal_20231024.log audit_report.json

# 使用 jq 工具分析特定键的操作历史
cat audit_report.json | jq '.entries[] | select(.key | startswith("user:"))'

# 统计敏感数据操作频率
./analyze-wal.sh analyze /data/lsm/wal_20231024.log | grep -E "(PUT|DELETE) operations"
```

**审计维度：**

- 数据访问模式分析
- 敏感键操作追踪
- 操作时间分布统计
- 数据修改频率评估

### 6.4 开发测试验证

在单元测试和集成测试中验证 WAL 写入逻辑的正确性：

```bash
# 验证测试用例生成的 WAL 文件格式
./analyze-wal.sh validate target/test-classes/wal_test_output.log

# 检查测试数据的操作序列正确性
./analyze-wal.sh analyze target/test-classes/wal_test_output.log --show-entries

# 导出测试结果用于断言验证
./analyze-wal.sh export target/test-classes/wal_test_output.log test_verification.json
```

**测试验证要点：**

- 验证操作序列的时间戳单调性
- 检查 PUT/DELETE 操作的数据完整性
- 确认测试场景覆盖的操作类型分布

## 7. 使用注意事项

### 7.1 常见问题与解决方案

#### 7.1.1 文件访问问题

1. **文件不存在**

   ```text
   错误: 文件不存在: /path/to/nonexistent.log
   ```

   **解决方案：** 检查文件路径是否正确，确认 WAL 文件已生成

2. **权限问题**

   ```text
   错误: 无法读取文件: /path/to/protected.log
   ```

   **解决方案：** 使用 `chmod 644` 或 `sudo` 确保文件可读权限

3. **文件锁定**

   ```text
   错误: 文件被其他进程占用: /data/lsm/current.wal
   ```

   **解决方案：** 等待写入进程完成或复制文件后分析

#### 7.1.2 格式验证问题

1. **格式错误**

   ```text
   ❌ WAL文件验证失败: invalid_wal.log
   发现 5 个无效条目 (行号: 23, 45, 67, 89, 101)
   ```

   **解决方案：** 检查指定行的格式，确保符合 `OPERATION|KEY|VALUE|TIMESTAMP` 规范

2. **编码问题**

   ```text
   警告: 检测到非UTF-8字符，可能影响解析准确性
   ```

   **解决方案：** 使用 `iconv -f GBK -t UTF-8` 转换文件编码

#### 7.1.3 性能与内存问题

1. **内存不足**

   ```text
   java.lang.OutOfMemoryError: Java heap space
   ```

   **解决方案：** 增加 JVM 堆内存 `export JAVA_OPTS="-Xmx4g"`

2. **分析超时**

   ```text
   分析超时: 文件过大 (>1GB)，建议分批处理
   ```

   **解决方案：** 使用 `split` 命令分割大文件或增加超时时间

### 7.2 文件处理策略

#### 7.2.1 大文件处理

**预处理检查：**

```bash
# 检查文件大小，超过100MB需要特殊处理
file_size=$(stat -f%z /data/lsm/large_wal_file.log 2>/dev/null || stat -c%s /data/lsm/large_wal_file.log)
if [ $file_size -gt 104857600 ]; then
    echo "大文件检测: ${file_size} bytes，建议分批处理"
fi

# 先验证文件完整性
./analyze-wal.sh validate /data/lsm/large_wal_file.log
```

**分批处理策略：**

```bash
# 将大文件分割为小块进行分析
split -l 10000 /data/lsm/huge_wal_file.log /tmp/wal_chunk_
for chunk in /tmp/wal_chunk_*; do
    ./analyze-wal.sh analyze "$chunk" >> combined_analysis.txt
done
```

#### 7.2.2 批量处理

**批量分析脚本：**

```bash
#!/bin/bash
# 批量分析 WAL 文件目录
WAL_DIR="/data/lsm"
OUTPUT_DIR="/data/analysis"

mkdir -p "$OUTPUT_DIR"

for wal_file in "$WAL_DIR"/wal_*.log; do
    if [ -f "$wal_file" ]; then
        base_name=$(basename "$wal_file" .log)

        # 检查是否已分析过
        if [ -f "$OUTPUT_DIR/${base_name}.json" ]; then
            echo "跳过已分析文件: $wal_file"
            continue
        fi

        echo "分析文件: $wal_file"
        ./analyze-wal.sh analyze "$wal_file" > "$OUTPUT_DIR/${base_name}.analysis"
        ./analyze-wal.sh export "$wal_file" "$OUTPUT_DIR/${base_name}.json"
    fi
done
```

### 7.3 健康检查

**WAL 文件健康检查脚本：**

```bash
#!/bin/bash
# WAL 文件健康检查脚本
WAL_DIR="/data/lsm"
LOG_FILE="/var/log/wal_health_check.log"

check_wal_health() {
    local wal_file="$1"

    # 验证文件格式
    if ! ./analyze-wal.sh validate "$wal_file" >/dev/null 2>&1; then
        echo "$(date): 验证失败 - $wal_file" >> "$LOG_FILE"
        return 1
    fi

    # 检查文件大小异常
    local file_size=$(stat -c%s "$wal_file" 2>/dev/null || stat -f%z "$wal_file")
    if [ $file_size -eq 0 ]; then
        echo "$(date): 空文件检测 - $wal_file" >> "$LOG_FILE"
        return 1
    fi

    return 0
}

# 检查所有 WAL 文件
failed_files=()
for wal_file in "$WAL_DIR"/wal_*.log; do
    if [ -f "$wal_file" ]; then
        if ! check_wal_health "$wal_file"; then
            failed_files+=("$wal_file")
        fi
    fi
done

# 输出检查结果
if [ ${#failed_files[@]} -gt 0 ]; then
    echo "发现 ${#failed_files[@]} 个问题文件，详情请查看 $LOG_FILE"
else
    echo "所有 WAL 文件检查通过"
fi
```

### 7.4 故障排除清单

#### 7.4.1 环境检查

- [ ] Java 版本兼容性（推荐 JDK 8+）
- [ ] 项目构建状态（`./build.sh` 成功执行）
- [ ] 文件系统权限（读取权限）
- [ ] 磁盘空间充足（临时文件和输出文件）

#### 7.4.2 文件检查

- [ ] WAL 文件存在且非空
- [ ] 文件编码为 UTF-8
- [ ] 文件格式符合规范
- [ ] 文件未被其他进程锁定

#### 7.4.3 工具检查

- [ ] `analyze-wal.sh` 脚本可执行
- [ ] Java 类路径配置正确
- [ ] 依赖库完整
- [ ] 输出目录可写
