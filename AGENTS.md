# Java LSM Tree 项目指南

> 本文档面向 AI 编程助手，用于快速理解本项目架构和开发规范。

## 项目概述

本项目是一个用 Java 8 实现的 Log-Structured Merge Tree (LSM Tree) 数据结构，专为写密集型工作负载优化。LSM Tree 的核心思想是**将随机写入转换为顺序写入**，从而充分利用磁盘的顺序访问性能优势。

### 主要特性

- **高性能写入**: O(log N) 写入性能
- **数据持久化**: WAL (Write-Ahead Log) 确保崩溃恢复
- **自动压缩**: 后台自动执行 SSTable 合并（支持 Level-based 和 Size-Tiered 两种策略）
- **并发安全**: 使用 ReadWriteLock 实现读写分离
- **布隆过滤器**: 减少无效磁盘 IO
- **范围查询**: 支持高效的范围扫描和反向扫描
- **数据压缩**: 支持 LZ4 压缩算法
- **缓存系统**: 内置 LRU/LFU 缓存策略，支持自定义配置
- **监控指标**: 集成 Micrometer 指标收集，支持 Prometheus 导出
- **内存优化**: 对象池、堆外内存管理等优化机制
- **异步 I/O**: 支持异步批处理 I/O 操作
- **分区支持**: 支持基于一致性哈希和范围的分区策略

## 技术栈

| 组件       | 版本/说明                               |
| ---------- | --------------------------------------- |
| Java       | 8（源代码和目标均为 Java 8）            |
| 构建工具   | Maven 3.8+                              |
| 测试框架   | JUnit 4.13.2                            |
| 指标监控   | Micrometer 1.10.5（含 Prometheus 支持） |
| 压缩算法   | LZ4 Java 1.8.0                          |
| 代码覆盖率 | JaCoCo 0.8.8（要求最低 33% 行覆盖率）   |

## 项目结构

```text
java-lsm-tree/
├── src/
│   ├── main/java/com/brianxiadong/lsmtree/    # 主源码
│   │   ├── LSMTree.java                  # 核心 LSM Tree 实现
│   │   ├── MemTable.java                 # 内存表（跳表实现）
│   │   ├── SSTable.java                  # 磁盘有序文件（SSTable）
│   │   ├── BloomFilter.java              # 布隆过滤器
│   │   ├── WriteAheadLog.java            # 写前日志（WAL）
│   │   ├── KeyValue.java                 # 键值数据结构
│   │   ├── CompactionStrategy.java       # 压缩策略接口
│   │   ├── LeveledCompactionStrategy.java    # 分层压缩实现（LevelDB 风格）
│   │   ├── SizeTieredCompactionStrategy.java # Size-Tiered 压缩实现
│   │   ├── CompressionStrategy.java      # 压缩策略接口
│   │   ├── LZ4CompressionStrategy.java   # LZ4 压缩实现
│   │   ├── NoneCompressionStrategy.java  # 无压缩实现
│   │   ├── RangeQuery.java               # 范围查询实现
│   │   ├── PartitionedLSMTree.java       # 分区 LSM Tree
│   │   ├── PartitionStrategy.java        # 分区策略接口
│   │   ├── ConsistentHashPartitionStrategy.java  # 一致性哈希分区
│   │   ├── RangePartitionStrategy.java   # 范围分区策略
│   │   ├── LSMTreeMetrics.java           # 指标接口
│   │   ├── MicrometerLSMTreeMetrics.java # Micrometer 指标实现
│   │   ├── NoopLSMTreeMetrics.java       # 空指标实现
│   │   ├── MetricsRegistry.java          # 指标注册中心
│   │   ├── MetricsHttpServer.java        # HTTP 指标服务器
│   │   ├── AsyncIO.java                  # 异步 I/O 接口定义
│   │   ├── AsyncIOBatch.java             # 异步 I/O 批处理
│   │   ├── AsyncIOManager.java           # 异步 I/O 管理器接口
│   │   ├── NioAsyncIOManager.java        # NIO 异步 I/O 实现
│   │   ├── LSMTreeExample.java           # 使用示例
│   │   ├── BenchmarkRunner.java          # 基准测试运行器
│   │   ├── cache/                        # 缓存模块
│   │   │   ├── CacheManager.java         # 缓存管理器接口
│   │   │   ├── CacheManagerImpl.java     # 缓存管理器实现
│   │   │   ├── LRUCache.java             # LRU 缓存实现
│   │   │   ├── LFUCache.java             # LFU 缓存实现
│   │   │   ├── Block.java                # 缓存块定义
│   │   │   ├── CacheStats.java           # 缓存统计
│   │   │   ├── CacheException.java       # 缓存异常
│   │   │   ├── CacheStrategy.java        # 缓存策略枚举
│   │   │   ├── CacheType.java            # 缓存类型枚举
│   │   │   └── InternalCache.java        # 内部缓存接口
│   │   ├── memory/                       # 内存管理模块
│   │   │   ├── MemoryManager.java        # 内存管理器接口
│   │   │   ├── DefaultMemoryManager.java # 默认内存管理器
│   │   │   ├── DirectMemoryManager.java  # 堆外内存管理器
│   │   │   ├── MemoryMonitor.java        # 内存监控
│   │   │   ├── MemoryUsageStats.java     # 内存使用统计
│   │   │   ├── ObjectPool.java           # 对象池接口
│   │   │   ├── GenericObjectPool.java    # 通用对象池实现
│   │   │   ├── PoolStats.java            # 对象池统计
│   │   │   ├── OptimizedMemTable.java    # 优化版内存表
│   │   │   ├── MemoryOptimizationTest.java # 内存优化测试运行器
│   │   │   └── GCConfig.java             # GC 配置
│   │   └── tools/                        # 工具类
│   │       ├── SSTableAnalyzer.java      # SSTable 分析器
│   │       ├── SSTableAnalyzerCLI.java   # SSTable 分析器 CLI
│   │       ├── WALAnalyzer.java          # WAL 分析器
│   │       ├── WALAnalyzerCLI.java       # WAL 分析器 CLI
│   │       └── RangeBenchmarkRunner.java # 范围查询基准测试工具
│   └── test/java/com/brianxiadong/lsmtree/    # 测试代码（51+ 测试类）
│       ├── LSMTreeTest.java              # 核心功能测试
│       ├── MemTableTest.java             # 内存表测试
│       ├── WriteAheadLogTest.java        # WAL 测试
│       ├── RangeQueryTest.java           # 范围查询测试
│       ├── *Test.java                    # 其他专项测试
│       ├── TestLogger.java               # 测试日志工具
│       └── TestConfig.java               # 测试配置
├── docs/                                 # 技术文档
├── test-suite/                           # 测试套件脚本
├── tutorials/                            # 学习教程（8 篇）
├── learn/                                # 学习计划和总结
├── pom.xml                               # Maven 配置
├── build.sh                              # Docker 构建脚本
├── analyze-db.sh                         # SSTable 分析工具
└── analyze-wal.sh                        # WAL 分析工具
```

## 核心架构

### 写入流程

```text
Write -> WAL (持久化) -> MemTable (内存跳表) -> (满了) -> SSTable (磁盘)
```

### 查询流程

```text
1. 查询 MemTable (活跃)
2. 查询 Immutable MemTables (按时间倒序)
3. 查询 SSTables (按创建时间倒序，使用布隆过滤器优化)
```

### 分层压缩架构

```text
Level 0: [SSTable] [SSTable] [SSTable] [SSTable]  (默认 4 个文件时触发压缩)
Level 1: [SSTable] [SSTable] ... (默认 40 个文件时触发压缩)
Level 2: [SSTable] [SSTable] ... (默认 400 个文件时触发压缩)
...
```

### 关键类说明

| 类名                           | 职责                                                  |
| ------------------------------ | ----------------------------------------------------- |
| `LSMTree`                      | 主入口类，整合所有组件，提供 put/get/delete/range API |
| `PartitionedLSMTree`           | 支持分区的 LSM Tree 实现，用于大规模数据集            |
| `MemTable`                     | 内存中的有序表，使用 `ConcurrentSkipListMap` 实现     |
| `SSTable`                      | 磁盘上的有序不可变文件，包含布隆过滤器                |
| `BloomFilter`                  | 布隆过滤器，快速判断键是否可能存在                    |
| `WriteAheadLog`                | 写前日志，确保崩溃恢复，使用同步 FileChannel          |
| `LeveledCompactionStrategy`    | 分层压缩策略（LevelDB 风格）                          |
| `SizeTieredCompactionStrategy` | Size-Tiered 压缩策略（Cassandra 风格）                |
| `RangeQuery`                   | 范围查询实现，支持正序和倒序扫描                      |
| `CacheManagerImpl`             | 缓存管理器，支持 LRU/LFU 策略                         |
| `AsyncIOManager`               | 异步 I/O 管理器，处理非阻塞文件操作                   |

## 构建和测试命令

### Maven 命令

```bash
# 编译
mvn clean compile

# 运行所有测试
mvn test

# 生成代码覆盖率报告
mvn clean test jacoco:report
# 报告位置: target/site/jacoco/index.html

# 打包（跳过测试）
mvn clean package -DskipTests=true

# 运行特定测试类
mvn test -Dtest=LSMTreeTest
mvn test -Dtest=MemTableTest

# 运行多个测试类
mvn test -Dtest=LSMTreeTest,RangeQueryTest
```

### 构建脚本（Docker 方式）

```bash
# 构建项目
./build.sh build

# 运行测试
./build.sh test

# 清理构建产物
./build.sh clean

# 显示帮助
./build.sh help
```

### 测试套件（推荐）

本项目提供了一个综合测试套件 [test-suite/test-suite.sh](test-suite/test-suite.sh)，集成了单元测试、功能测试、性能测试和压力测试。

**基本用法**:

```text
./test-suite/test-suite.sh [命令] [参数]
```

**常用命令**:

```bash
# 运行所有测试（默认）
./test-suite/test-suite.sh all

# 运行特定测试类型
./test-suite/test-suite.sh unit         # 单元测试
./test-suite/test-suite.sh functional   # 功能测试
./test-suite/test-suite.sh tools        # 工具与 CLI 测试
./test-suite/test-suite.sh performance  # 性能测试
./test-suite/test-suite.sh stress       # 压力测试

# 专项测试
./test-suite/test-suite.sh cache-benchmark # 缓存策略对比基准测试
./test-suite/test-suite.sh memory-opt      # 内存优化专项测试（百万级数据）

# 会话管理
./test-suite/test-suite.sh list                    # 列出所有测试会话
./test-suite/test-suite.sh show <session-id>       # 查看会话详情
./test-suite/test-suite.sh delete <session-id>     # 删除会话
./test-suite/test-suite.sh report <session-id>     # 重新生成会话报告
./test-suite/test-suite.sh archive <days>          # 归档指定天数前的会话

# 环境清理
./test-suite/test-suite.sh clean                   # 清理当前测试环境
./test-suite/test-suite.sh clean-all               # 清理所有测试数据和历史记录
```

### 分析工具

```bash
# 分析 SSTable 文件
./analyze-db.sh <SSTable文件路径>

# 分析 WAL 文件
./analyze-wal.sh <WAL文件路径>
```

## 代码规范

### 命名规范

- 类名：PascalCase（如 `LSMTree`, `MemTable`）
- 方法名：camelCase（如 `put()`, `get()`）
- 常量：UPPER_SNAKE_CASE
- 包名：全小写，使用域名反转（如 `com.brianxiadong.lsmtree`）

### 注释规范

- 类和方法使用 Javadoc 风格注释
- 关键逻辑添加中文注释解释设计意图
- 复杂算法需注明时间/空间复杂度

示例：

```java
/**
 * 插入键值对
 * 时间复杂度: O(log N) - 跳表插入
 */
public void put(String key, String value) throws IOException {
    // 写入 WAL 确保持久性
    wal.append(WriteAheadLog.LogEntry.put(key, value));
    // 写入活跃 MemTable
    activeMemTable.put(key, value);
}
```

### 并发控制

- 使用 `ReadWriteLock` 实现读写分离
- 写操作获取写锁，读操作获取读锁
- WAL 写入使用同步机制确保顺序性
- 后台压缩任务使用单线程线程池

### 异常处理

- 使用自定义异常或标准 `IOException`
- 关键操作必须清理资源（使用 try-finally 或 try-with-resources）
- 禁止吞掉异常，至少打印堆栈信息

## 测试规范

### 测试结构

项目包含 51+ 个测试类，每个测试类独立管理自己的生命周期：

```text
src/test/java/com/brianxiadong/lsmtree/
├── LSMTreeTest.java                    # 核心功能测试
├── MemTableTest.java                   # 内存表测试
├── WriteAheadLogTest.java              # WAL 测试
├── RangeQueryTest.java                 # 范围查询测试
├── LeveledCompactionStrategyTest.java  # 分层压缩测试
├── SizeTieredCompactionStrategyTest.java # Size-Tiered 压缩测试
├── Cache*Test.java                     # 缓存相关测试
├── *Test.java                          # 其他专项测试
├── TestLogger.java                     # 测试日志工具
└── TestConfig.java                     # 测试配置
```

### 测试模式

每个测试类遵循以下模式：

```java
public class LSMTreeTest {
    private LSMTree lsmTree;
    private String testDir;
    private TestLogger log;

    @Before
    public void setUp() throws IOException {
        testDir = "test_data_" + System.currentTimeMillis();
        lsmTree = new LSMTree(testDir, 100);
        log = new TestLogger("");
    }

    @After
    public void tearDown() throws IOException {
        if (lsmTree != null) {
            lsmTree.close();
        }
        deleteDirectory(new File(testDir));
    }

    @Test
    public void testBasicPutAndGet() throws IOException {
        // 测试代码
    }
}
```

### 测试工具

**TestLogger** 提供结构化日志输出：

```java
log = new TestLogger("测试名称");
log.start("开始测试");
log.step("执行步骤");
log.data("变量名", value);
log.assertSuccess("断言通过");
log.pass();  // 测试通过
```

### 测试数据目录

测试数据默认存放在项目根目录下的 `test_data_<timestamp>/` 目录中，测试结束后自动清理。

### 代码覆盖率要求

- 最低行覆盖率：33%（由 JaCoCo 强制执行）
- 生成报告：`mvn clean test jacoco:report`
- 报告位置：`target/site/jacoco/index.html`

## 性能优化要点

### MemTable 大小选择

| 大小    | 特点                       | 适用场景     |
| ------- | -------------------------- | ------------ |
| 1K-5K   | 低内存占用，频繁刷盘       | 内存受限环境 |
| 10K-20K | 平衡内存和性能             | 一般场景     |
| 50K+    | 高写入吞吐量，需要更多内存 | 写密集型场景 |

### 压缩策略配置

**Leveled Compaction（默认）**:

- Level 0 最大文件数：4
- 每级大小倍数：10
- 适合：读密集型、需要空间效率的场景

**Size-Tiered Compaction**:

- 最小文件数阈值：4
- 大小倍数：1.5
- 适合：写密集型、能接受更多空间放大的场景

### WAL 同步配置

可通过系统属性配置：

- `lsm.wal.sync.batch`：批处理大小（默认 64）
- `lsm.wal.sync.interval.ms`：同步间隔毫秒（默认 50ms）

### 缓存配置

```java
CacheManager cacheManager = new CacheManagerImpl(
    CacheStrategy.LRU,  // 或 CacheStrategy.LFU
    1000,               // 最大缓存块数
    8192                // 每块大小（字节）
);
lsmTree.setCacheManager(cacheManager);
```

### 内存优化

- 使用 `OptimizedMemTable` 替代标准 `MemTable` 以获得更好的内存效率
- 配置 `DirectMemoryManager` 使用堆外内存
- 使用 `GenericObjectPool` 复用频繁创建的对象

## 安全注意事项

1. **数据目录权限**：确保数据目录有适当的文件系统权限
2. **WAL 文件保护**：WAL 文件包含未刷盘的所有数据，需定期清理
3. **资源释放**：使用 `try-with-resources` 或确保调用 `LSMTree.close()`
4. **并发访问**：虽然 LSMTree 是线程安全的，但避免在回调中执行耗时操作

## 常用开发工作流

### 添加新功能

1. 在 `src/main/java/com/brianxiadong/lsmtree/` 下创建或修改类
2. 编写对应的单元测试（命名 `XxxTest.java`）
3. 运行 `./test-suite/test-suite.sh unit` 确保测试通过
4. 运行 `mvn clean test jacoco:report` 确保覆盖率达标

### 调试技巧

```bash
# 运行特定测试并查看详细输出
mvn test -Dtest=LSMTreeTest -X

# 使用分析工具检查数据文件
./analyze-db.sh data/sstable_level0_xxx.db
./analyze-wal.sh data/wal.log

# 查看指标（如启用 HTTP 服务器）
curl http://localhost:9090/metrics
```

### 性能调优

```bash
# 运行性能基准测试
./test-suite/test-suite.sh performance

# 运行内存优化测试（百万级数据）
./test-suite/test-suite.sh memory-opt

# 查看性能报告
./test-suite/test-suite.sh show latest
```

## 参考资料

- [README.md](README.md) - 项目完整说明
- [docs/02-lsm-tree-deep-dive.md](docs/02-lsm-tree-deep-dive.md) - LSM Tree 深度解析
- [docs/06-benchmark-guide.md](docs/06-benchmark-guide.md) - 性能基准测试指南
- [tutorials/advanced/production-readiness-analysis.md](tutorials/advanced/production-readiness-analysis.md) - 生产就绪性分析（关键）
- [docs/05-test-suite-guide.md](docs/05-test-suite-guide.md) - 测试套件使用说明
- [tutorials/advanced/memory-optimization-guide.md](tutorials/advanced/memory-optimization-guide.md) - 内存优化指南
- [tutorials/](tutorials/) - 详细实现教程（8 篇）
