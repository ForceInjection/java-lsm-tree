# Java LSM Tree 测试套件使用指南

## 1. 概述

Java LSM Tree 测试套件是一个模块化的自动化测试框架，提供全面的测试覆盖和专业的测试管理功能。测试套件基于 Docker 容器化执行，确保测试环境的一致性和可重复性。

**核心特性**:

- **多维度测试覆盖** - 支持单元测试、功能测试、性能测试、内存测试、压力测试和工具测试
- **会话管理系统** - 每次测试运行生成唯一会话 ID，支持历史记录查看和管理
- **自动化报告生成** - 自动生成详细的测试报告和性能分析数据
- **环境隔离** - 基于 Docker 的容器化执行，确保测试环境一致性
- **超时控制** - 所有测试都配置了合理的超时时间，避免无限等待

---

## 2. 测试套件架构

### 2.1 模块化设计

测试套件采用模块化架构，包含以下核心模块:

```text
测试套件架构:
├── test-suite.sh              # 主入口脚本
├── lib/
│   ├── common.sh             # 公共函数和配置
│   ├── session.sh            # 会话管理
│   ├── tests.sh              # 测试执行逻辑
│   └── reports.sh            # 报告生成
└── results/
    ├── sessions/             # 会话目录
    │   └── YYYYMMDD_HHMMSS/ # 会话ID目录
    │       ├── unit/         # 单元测试结果
    │       ├── functional/   # 功能测试结果
    │       ├── performance/  # 性能测试结果
    │       ├── memory/       # 内存测试结果
    │       ├── stress/       # 压力测试结果
    │       ├── tools/        # 工具测试结果
    │       └── test_results.json # 测试结果汇总
    └── archive/              # 归档的会话
```

### 2.2 测试类型说明

| 测试类型     | 描述                 | 主要验证内容           | 超时配置 |
| ------------ | -------------------- | ---------------------- | -------- |
| **单元测试** | 验证核心组件的正确性 | 类方法逻辑、边界条件   | 无超时   |
| **功能测试** | 验证系统功能完整性   | API 接口、数据持久化   | 20-60 秒 |
| **性能测试** | 评估系统性能指标     | 吞吐量、延迟、资源使用 | 120 秒   |
| **内存测试** | 检测内存使用情况     | 内存泄漏、GC 行为      | 30 秒    |
| **压力测试** | 验证系统稳定性       | 高并发、长时间运行     | 60 秒    |
| **工具测试** | 验证辅助工具功能     | CLI 工具、分析工具     | 无超时   |

---

## 3. 安装和环境要求

### 3.1 必需软件

```bash
# Docker 引擎 (必需)
docker --version
# Docker Compose (可选)
docker-compose --version
# Maven (用于本地开发)
mvn --version
# Java 8/11/17 (用于本地开发)
java -version
```

### 3.2 环境配置

测试套件使用以下环境变量进行配置:

```bash
# 测试执行超时配置(秒)
export FUNCTIONAL_EXAMPLE_TIMEOUT=20     # 功能示例超时
export FUNCTIONAL_METRICS_TIMEOUT=15     # 指标测试超时
export FUNCTIONAL_API_TIMEOUT=20         # API 测试超时
export PERFORMANCE_TIMEOUT=120           # 性能测试超时
export MEMORY_TIMEOUT=30                 # 内存测试超时
export STRESS_TIMEOUT=60                 # 压力测试超时

# Java 选项
export JAVA_OPTS="-Xmx2g -Xms1g"

# Maven 配置
export MAVEN_OPTS="-Xmx1g"
```

---

## 4. 使用方法

### 4.1 基本命令

```bash
# 运行所有测试
./test-suite/test-suite.sh all

# 运行特定类型的测试
./test-suite/test-suite.sh unit          # 单元测试
./test-suite/test-suite.sh functional    # 功能测试
./test-suite/test-suite.sh performance   # 性能测试
./test-suite/test-suite.sh memory        # 内存测试
./test-suite/test-suite.sh stress        # 压力测试
./test-suite/test-suite.sh tools         # 工具测试

# 命令别名支持
./test-suite/test-suite.sh func          # functional 的别名
./test-suite/test-suite.sh perf          # performance 的别名
./test-suite/test-suite.sh mem           # memory 的别名
```

### 4.2 会话管理命令

```bash
# 列出所有测试会话
./test-suite/test-suite.sh list

# 查看特定会话的详细信息
./test-suite/test-suite.sh show <会话ID>

# 删除特定会话
./test-suite/test-suite.sh delete <会话ID>

# 重新生成会话报告
./test-suite/test-suite.sh report <会话ID>
```

### 4.3 环境管理命令

```bash
# 清理当前测试环境
./test-suite/test-suite.sh clean

# 清理所有测试数据（包括历史会话）
./test-suite/test-suite.sh clean-all

# 归档旧会话
./test-suite/test-suite.sh archive [天数]  # 默认归档30天前的会话
```

### 4.4 帮助信息

```bash
# 显示完整的帮助信息
./test-suite/test-suite.sh help
./test-suite/test-suite.sh --help
./test-suite/test-suite.sh -h
```

---

## 5. 测试执行详解

### 5.1 单元测试 (unit)

单元测试验证核心组件的正确性，使用 Maven Surefire 插件执行:

```bash
# 执行命令
docker run --rm \
  -v "${PROJECT_ROOT}":/workspace \
  -v "${HOME}/.m2":/root/.m2 \
  -w /workspace \
  maven:3.8.6-openjdk-8 \
  mvn -q -DskipTests=false -DfailIfNoTests=false test
```

**验证内容**:

- 所有核心类的方法逻辑正确性
- 边界条件和异常处理
- 线程安全和并发行为

### 5.2 功能测试 (functional)

功能测试包含多个子分组，验证系统功能的完整性:

#### 5.2.1 基本功能示例 (example)

```bash
# 执行 LSMTreeExample 程序
docker run --rm \
  -v "${PROJECT_ROOT}":/workspace \
  -v "${HOME}/.m2":/root/.m2 \
  -w /workspace \
  maven:3.8.6-openjdk-8 \
  bash -lc "timeout ${FUNCTIONAL_EXAMPLE_TIMEOUT}s mvn exec:java \
    -Dexec.mainClass='com.brianxiadong.lsmtree.LSMTreeExample' \
    -Dexec.args='lsm_data' \
    -Dexec.cleanupDaemonThreads=true \
    -Dexec.daemonThreadJoinTimeout=2000 \
    -Dexec.stopWait=2000"
```

**验证内容**:

- 程序正常启动和退出
- 基本的读写操作功能
- 数据目录创建和文件生成

#### 5.2.2 存储基础 IO (storage.basic_io)

验证 WAL 日志和 SSTable 文件的生成:

- 检查 `wal.log` 文件存在性
- 检查 `sstable_*` 文件存在性
- 验证文件格式正确性

#### 5.2.3 指标暴露 (metrics.expose)

```bash
# 启用指标服务器执行
docker run --rm \
  -v "${PROJECT_ROOT}":/workspace \
  -v "${HOME}/.m2":/root/.m2 \
  -w /workspace \
  -e MAVEN_OPTS="-Dlsm.metrics.http.enabled=true -Dlsm.metrics.http.port=9093" \
  maven:3.8.6-openjdk-8 \
  bash -lc "timeout ${FUNCTIONAL_METRICS_TIMEOUT}s mvn exec:java \
    -Dexec.mainClass='com.brianxiadong.lsmtree.LSMTreeExample' \
    -Dexec.args='lsm_data' \
    -Dexec.cleanupDaemonThreads=true \
    -Dexec.daemonThreadJoinTimeout=2000 \
    -Dexec.stopWait=2000"
```

**验证内容**:

- 指标服务器正常启动
- HTTP 端口监听功能
- 程序正常退出

#### 5.2.4 API 接口 (api.put_get)

```bash
# 使用 BenchmarkRunner 验证 API
docker run --rm \
  -v "${PROJECT_ROOT}":/workspace \
  -v "${HOME}/.m2":/root/.m2 \
  -w /workspace \
  maven:3.8.6-openjdk-8 \
  bash -lc "timeout ${FUNCTIONAL_API_TIMEOUT}s mvn exec:java \
    -Dexec.mainClass='com.brianxiadong.lsmtree.BenchmarkRunner' \
    -Dexec.args='--operations 50 --threads 1 --key-size 8 --value-size 16 --data-dir api_data' \
    -Dexec.cleanupDaemonThreads=true \
    -Dexec.daemonThreadJoinTimeout=2000 \
    -Dexec.stopWait=2000"
```

**验证内容**:

- PUT 和 GET 操作基本功能
- 多线程环境下的 API 稳定性
- 小规模数据集的正确性

### 5.3 性能测试 (performance)

性能测试运行多轮基准测试，收集详细的性能指标:

```bash
# 性能测试执行
docker run --rm \
  -v "${PROJECT_ROOT}":/workspace \
  -v "${HOME}/.m2":/root/.m2 \
  -w /workspace \
  maven:3.8.6-openjdk-8 \
  bash -lc "timeout ${PERFORMANCE_TIMEOUT}s mvn exec:java \
    -Dexec.mainClass='com.brianxiadong.lsmtree.BenchmarkRunner' \
    -Dexec.args='--operations ${PERFORMANCE_OPS} --threads ${PERFORMANCE_THREADS} --key-size 16 --value-size 100 --data-dir benchmark_data' \
    -Dexec.cleanupDaemonThreads=true \
    -Dexec.daemonThreadJoinTimeout=2000 \
    -Dexec.stopWait=2000"
```

**收集指标**:

- 吞吐量 (ops/sec)
- 延迟分布 (P50, P95, P99)
- 内存使用情况
- CPU 使用率
- 磁盘 I/O 统计

### 5.4 内存测试 (memory)

内存测试专注于检测内存使用模式和潜在的内存泄漏:

```bash
# 内存测试执行
docker run --rm \
  -v "${PROJECT_ROOT}":/workspace \
  -v "${HOME}/.m2":/root/.m2 \
  -w /workspace \
  maven:3.8.6-openjdk-8 \
  bash -lc "timeout ${MEMORY_TIMEOUT}s mvn exec:java \
    -Dexec.mainClass='com.brianxiadong.lsmtree.BenchmarkRunner' \
    -Dexec.args='--operations 5000 --threads 2 --key-size 16 --value-size 100 --data-dir memory_data' \
    -Dexec.cleanupDaemonThreads=true \
    -Dexec.daemonThreadJoinTimeout=2000 \
    -Dexec.stopWait=2000"
```

**检测内容**:

- 内存泄漏模式
- GC 行为分析
- 内存使用趋势
- 对象分配模式

### 5.5 压力测试 (stress)

压力测试验证系统在高负载下的稳定性和可靠性:

```bash
# 压力测试执行
docker run --rm \
  -v "${PROJECT_ROOT}":/workspace \
  -v "${HOME}/.m2":/root/.m2 \
  -w /workspace \
  maven:3.8.6-openjdk-8 \
  bash -lc "timeout ${STRESS_TIMEOUT}s mvn exec:java \
    -Dexec.mainClass='com.brianxiadong.lsmtree.BenchmarkRunner' \
    -Dexec.args='--operations 10000 --threads 8 --key-size 32 --value-size 512 --data-dir stress_data' \
    -Dexec.cleanupDaemonThreads=true \
    -Dexec.daemonThreadJoinTimeout=2000 \
    -Dexec.stopWait=2000"
```

**验证内容**:

- 高并发下的系统稳定性
- 资源竞争处理
- 错误恢复能力
- 长时间运行的可靠性

### 5.6 工具测试 (tools)

工具测试验证辅助工具类的功能正确性:

```bash
# 工具测试执行
docker run --rm \
  -v "${PROJECT_ROOT}":/workspace \
  -v "${HOME}/.m2":/root/.m2 \
  -w /workspace \
  maven:3.8.6-openjdk-8 \
  mvn -q -Dtest=com.brianxiadong.lsmtree.tools.* -DfailIfNoTests=false test
```

**测试工具类**:

- `WALAnalyzer` - WAL 文件分析工具
- `SSTableAnalyzer` - SSTable 文件分析工具
- 其他工具和工具类

---

## 6. 测试结果分析

### 6.1 结果目录结构

每次测试运行都会在 `test-suite/results/sessions/` 目录下创建会话目录:

```text
YYYYMMDD_HHMMSS/                 # 会话ID目录
├── unit/                       # 单元测试结果
│   ├── unit_test_TIMESTAMP.log
│   └── surefire-reports/       # Surefire 测试报告
├── functional/                 # 功能测试结果
│   ├── example/               # 基本功能示例
│   ├── storage/               # 存储测试
│   ├── metrics/               # 指标测试
│   └── api/                   # API 测试
├── performance/               # 性能测试结果
│   └── benchmark_TIMESTAMP.log
├── memory/                    # 内存测试结果
│   └── memory_test_TIMESTAMP.log
├── stress/                    # 压力测试结果
│   └── stress_test_TIMESTAMP.log
├── tools/                     # 工具测试结果
│   └── tools_test_TIMESTAMP.log
└── test_results.json          # 测试结果汇总文件
```

### 6.2 测试结果文件格式

`test_results.json` 文件包含所有测试的汇总结果:

```json
{
  "session_id": "20241201_143022",
  "start_time": "2024-12-01 14:30:22",
  "end_time": "2024-12-01 14:35:18",
  "duration_seconds": 296,
  "test_categories": {
    "unit": {
      "status": "completed",
      "results": {
        "maven_surefire": "PASS"
      }
    },
    "functional": {
      "status": "completed",
      "results": {
        "example.run": "PASS",
        "storage.basic_io": "PASS",
        "metrics.expose": "PASS",
        "api.put_get": "PASS"
      }
    }
  }
}
```

### 6.3 性能测试报告

性能测试生成详细的文本报告:

```text
=== Java LSM Tree 性能基准测试结果 ===
测试时间: 2024-12-01 14:32:15
Java 版本: openjdk version "1.8.0_392"
JVM 参数: -Xmx2g -Xms1g

=== 第1轮性能测试 ===
总操作数: 100000
错误数: 0 (0.00%)
总耗时: 2.45 秒
吞吐量: 40816.33 ops/sec
平均延迟: 0.02 ms
P50 延迟: 0.01 ms
P95 延迟: 0.05 ms
P99 延迟: 0.12 ms
最大延迟: 15.23 ms

=== 内存使用统计 ===
初始内存: 128.5 MB
峰值内存: 256.8 MB
最终内存: 189.2 MB
内存增长: 60.7 MB
```

---

## 7. 高级用法和配置

### 7.1 自定义超时配置

可以通过环境变量调整各类测试的超时时间:

```bash
# 功能测试超时配置
export FUNCTIONAL_EXAMPLE_TIMEOUT=30     # 示例测试超时(默认20秒)
export FUNCTIONAL_METRICS_TIMEOUT=20     # 指标测试超时(默认15秒)
export FUNCTIONAL_API_TIMEOUT=25         # API测试超时(默认20秒)

# 性能测试超时配置
export PERFORMANCE_TIMEOUT=180           # 性能测试超时(默认120秒)

# 内存测试超时配置
export MEMORY_TIMEOUT=45                 # 内存测试超时(默认30秒)

# 压力测试超时配置
export STRESS_TIMEOUT=90                 # 压力测试超时(默认60秒)
```

### 7.2 性能测试参数配置

```bash
# 性能测试操作数量配置
export PERFORMANCE_OPS=50000            # 每轮测试的操作数量
export PERFORMANCE_THREADS=4           # 并发线程数
export PERFORMANCE_ITERATIONS=3        # 测试轮次

# 性能测试数据大小配置
export PERFORMANCE_KEY_SIZE=16         # 键大小(字节)
export PERFORMANCE_VALUE_SIZE=100       # 值大小(字节)
```

### 7.3 Docker 配置

```bash
# 使用不同的 Maven 版本
export MAVEN_VERSION="3.9.6-eclipse-temurin-17"

# 使用不同的 JDK 版本
export JDK_VERSION="openjdk:8"

# 自定义 Docker 网络配置
export DOCKER_NETWORK="host"
```

### 7.4 调试模式

```bash
# 启用详细日志输出
export VERBOSE=1

# 启用调试模式
export DEBUG=1

# 保留临时文件
export KEEP_TEMP_FILES=1
```

---

## 8. 故障排除和常见问题

### 8.1 常见错误和解决方案

#### 8.1.1 Docker 相关问题

**错误**: `Cannot connect to the Docker daemon`

- **原因**: Docker 服务未启动
- **解决方案**: 启动 Docker 服务

  ```bash
  sudo systemctl start docker
  ```

**错误**: `Permission denied while trying to connect to the Docker daemon socket`

- **原因**: 当前用户不在 docker 组
- **解决方案**: 将用户添加到 docker 组

  ```bash
  sudo usermod -aG docker $USER
  newgrp docker
  ```

#### 8.1.2 超时相关问题

**错误**: 测试执行超时

- **原因**: 测试执行时间超过配置的超时时间
- **解决方案**: 增加超时配置或优化测试性能

  ```bash
  export FUNCTIONAL_EXAMPLE_TIMEOUT=60
  ```

#### 8.1.3 内存相关问题

**错误**: `Java heap space` 或 `OutOfMemoryError`

- **原因**: JVM 堆内存不足
- **解决方案**: 增加 JVM 堆内存

  ```bash
  export JAVA_OPTS="-Xmx4g -Xms2g"
  ```

#### 8.1.4 网络相关问题

**错误**: 端口冲突或网络连接问题

- **原因**: 端口被占用或网络配置问题
- **解决方案**: 使用不同的端口或检查网络配置

  ```bash
  export METRICS_HTTP_PORT=9094
  ```

### 8.2 性能优化建议

#### 8.2.1 测试执行优化

```bash
# 使用本地 Maven 仓库缓存
export MAVEN_OPTS="-Dmaven.repo.local=${HOME}/.m2/repository"

# 启用 Docker 构建缓存
export DOCKER_BUILDKIT=1

# 使用更快的存储驱动
export DOCKER_DRIVER="overlay2"
```

#### 8.2.2 资源使用优化

```bash
# 限制 Docker 容器资源使用
export DOCKER_MEMORY_LIMIT="2g"
export DOCKER_CPU_LIMIT="2"

# 优化 JVM 垃圾回收
export JAVA_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

---

## 9. 最佳实践

### 9.1 测试执行最佳实践

1. **环境一致性**: 始终在相同的环境中运行测试以确保结果可比性
2. **多次运行**: 重要的性能测试应该运行多次取平均值
3. **资源监控**: 在测试运行时监控系统资源使用情况
4. **日志分析**: 仔细分析测试日志以识别潜在问题
5. **版本控制**: 对测试配置和参数进行版本控制

### 9.2 结果分析最佳实践

1. **建立基线**: 为性能测试建立稳定的性能基线
2. **趋势分析**: 关注性能指标的变化趋势而非单次结果
3. **异常检测**: 设置合理的阈值来检测性能异常
4. **根本原因分析**: 对失败测试进行深入的根因分析
5. **持续改进**: 基于测试结果持续优化系统和测试本身

### 9.3 持续集成集成

```yaml
# GitHub Actions 配置示例
name: Java LSM Tree Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest

    services:
      docker:
        image: docker:dind
        options: --privileged

    steps:
      - name: Checkout code
        uses: actions/checkout@v3

      - name: Set up Docker
        run: |
          sudo groupadd docker || true
          sudo usermod -aG docker $USER
          newgrp docker

      - name: Run test suite
        run: ./test-suite/test-suite.sh all
        env:
          DOCKER_HOST: unix:///var/run/docker.sock
```

---

## 10. 变更记录

### 版本 1.0.0 (2024-12-01)

- 初始版本发布
- 支持完整的测试套件功能
- 包含单元测试、功能测试、性能测试、内存测试、压力测试和工具测试
- 实现会话管理系统和报告生成功能

### 版本 1.1.0 (2024-12-15)

- 增加超时控制机制，解决测试进程卡住的问题
- 优化 MetricsHttpServer 的资源清理
- 改进测试日志格式和可读性
- 增强错误处理和故障恢复能力

## 11. 监控启用指南

11.1 启用应用端指标

在运行示例或基准时启用 HTTP 指标输出，端口可配置：

```bash
mvn exec:java -Dexec.mainClass='com.brianxiadong.lsmtree.LSMTreeExample' \
  -Dlsm.metrics.http.enabled=true \
  -Dlsm.metrics.http.port=9091
```

11.2 启动 Prometheus 采集

使用示例配置文件：`examples/monitoring/prometheus.yml`。

```bash
docker run --rm -p 9090:9090 \
  -v "$(pwd)/examples/monitoring/prometheus.yml":/etc/prometheus/prometheus.yml \
  prom/prometheus --config.file=/etc/prometheus/prometheus.yml
```

可选：加载告警规则 `examples/monitoring/alerts.yml` 并配合 Alertmanager 使用。

11.3 启动 Grafana 并导入仪表盘

```bash
docker run --rm -p 3000:3000 grafana/grafana
```

登录 Grafana 后导入 `examples/monitoring/grafana-dashboard.json` 仪表盘，并将数据源指向 Prometheus（默认 <http://localhost:9090>）。

11.4 测试套件中快速验证指标

```bash
./test-suite/test-suite.sh functional
```

功能测试中的 `metrics.expose` 会以启用指标的方式运行示例程序，用于快速校验指标端口可用。

---

## 附录

### A. 环境变量参考

| 环境变量                     | 描述                 | 默认值          |
| ---------------------------- | -------------------- | --------------- |
| `FUNCTIONAL_EXAMPLE_TIMEOUT` | 功能示例测试超时(秒) | 20              |
| `FUNCTIONAL_METRICS_TIMEOUT` | 指标测试超时(秒)     | 15              |
| `FUNCTIONAL_API_TIMEOUT`     | API 测试超时(秒)     | 20              |
| `PERFORMANCE_TIMEOUT`        | 性能测试超时(秒)     | 120             |
| `MEMORY_TIMEOUT`             | 内存测试超时(秒)     | 30              |
| `STRESS_TIMEOUT`             | 压力测试超时(秒)     | 60              |
| `PERFORMANCE_OPS`            | 性能测试操作数量     | 100000          |
| `PERFORMANCE_THREADS`        | 性能测试线程数       | CPU 核心数      |
| `PERFORMANCE_ITERATIONS`     | 性能测试轮次         | 3               |
| `JAVA_OPTS`                  | JVM 选项             | `-Xmx2g -Xms1g` |
| `MAVEN_OPTS`                 | Maven 选项           | `-Xmx1g`        |

### B. 常用命令参考

```bash
# 快速验证
./test-suite/test-suite.sh unit
./test-suite/test-suite.sh functional

# 完整测试
./test-suite/test-suite.sh all

# 查看测试历史
./test-suite/test-suite.sh list

# 清理环境
./test-suite/test-suite.sh clean
```

### C. 相关文档

- [BenchmarkRunner 使用指南](./benchmark-guide.md)
- [性能分析指南](./performance-analysis-guide.md)
- [源码解析文档](./soucrce-code-analysis.md)
- [WAL 分析工具指南](./wal-analyzer-guide.md)
- [SSTable 分析工具指南](./db-analyzer-guide.md)
