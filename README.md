# Java LSM Tree 实现

![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)
![Java](https://img.shields.io/badge/java-8+-orange.svg)
![Maven](https://img.shields.io/badge/maven-3.8+-green.svg)

一个用 Java 实现的 Log-Structured Merge Tree (LSM Tree)数据结构，包含所有 LSM Tree 的核心特性。

## 1. LSM Tree 简介

### 1.1 什么是 LSM Tree

LSM Tree（Log-Structured Merge Tree）是一种专为写密集型工作负载优化的数据结构，最早由 Patrick O'Neil 等人在 1996 年的经典论文中提出。LSM Tree 的核心思想是**将随机写入转换为顺序写入**，从而充分利用磁盘的顺序访问性能优势。

### 1.2 核心设计思想

传统的 B+Tree 结构在处理大量写入操作时，由于需要维护树结构的平衡性，往往产生大量的随机 I/O 操作，导致性能瓶颈。LSM Tree 通过以下设计解决了这个问题：

- **分层存储架构**：将数据分为内存层和磁盘层，新数据首先写入内存，然后批量刷写到磁盘
- **顺序写入优化**：所有磁盘写入都是顺序的，避免了随机 I/O 的性能损失
- **Rolling Merge 算法**：通过后台的合并过程，将小文件逐步合并为大文件，保持数据的有序性

### 1.3 应用场景

LSM Tree 特别适合以下场景：

- **写密集型应用**：日志系统、时序数据库、监控系统
- **大数据存储**：分布式数据库、NoSQL 系统
- **高并发写入**：实时数据采集、事件流处理

现代许多知名系统都采用了 LSM Tree 的核心思想，包括 Google BigTable、LevelDB/RocksDB、Cassandra、HBase 等。

---

## 2. 特性与架构

### 2.1 核心 LSM Tree 组件

- **MemTable**: 内存中的有序数据结构，使用跳表实现
- **SSTable**: 磁盘上的有序不可变文件
- **WAL (Write-Ahead Log)**: 写前日志，确保数据持久性
- **布隆过滤器**: 快速判断键是否可能存在
- **压缩策略**: 多级合并压缩，优化存储和查询性能

### 2.2 主要功能

- ✅ **高性能写入**: O(log N) 写入性能
- ✅ **高效查询**: 结合内存和磁盘的多层查询
- ✅ **数据持久化**: WAL 确保崩溃恢复
- ✅ **自动压缩**: 后台自动执行 SSTable 合并
- ✅ **并发安全**: 读写锁保证线程安全
- ✅ **空间优化**: 布隆过滤器减少无效磁盘 IO

### 2.3 架构设计

#### 2.3.1 LSM Tree 结构

```text
写入流程: Write -> WAL -> MemTable -> (满了) -> SSTable
查询流程: MemTable -> Immutable MemTables -> SSTables (按时间倒序)
```

#### 2.3.2 分层压缩

```text
Level 0: [SSTable] [SSTable] [SSTable] [SSTable]  (4个文件时触发压缩)
Level 1: [SSTable] [SSTable] ... (40个文件时触发压缩)
Level 2: [SSTable] [SSTable] ... (400个文件时触发压缩)
...
```

---

## 3. 快速开始

### 3.1 环境要求

- Java 8 或更高版本
- Maven 3.6 或更高版本

### 3.2 安装和构建

```bash
# 克隆项目
git clone https://github.com/brianxiadong/java-lsm-tree.git
cd java-lsm-tree

# 使用构建脚本 (推荐)
./build.sh

# 或者查看构建选项
./build.sh help

# 传统 Maven 构建
mvn clean compile

# 运行测试
mvn test

# 打包
mvn package
```

### 3.3 基本使用

```java
import com.brianxiadong.lsmtree.LSMTree;

// 创建LSM Tree实例
try (LSMTree lsmTree = new LSMTree("data", 1000)) {
    // 插入数据
    lsmTree.put("user:1", "Alice");
    lsmTree.put("user:2", "Bob");

    // 查询数据
    String value = lsmTree.get("user:1"); // 返回 "Alice"

    // 更新数据
    lsmTree.put("user:1", "Alice Updated");

    // 删除数据
    lsmTree.delete("user:2");

    // 强制刷盘
    lsmTree.flush();

    // 获取统计信息
    LSMTree.LSMTreeStats stats = lsmTree.getStats();
    System.out.println(stats);
}
```

### 3.4 运行示例

```bash
# 运行示例程序
java -cp target/java-lsm-tree-1.0.0.jar com.brianxiadong.lsmtree.examples.BasicExample

# 运行性能测试
java -cp target/java-lsm-tree-1.0.0.jar com.brianxiadong.lsmtree.examples.PerformanceExample

# 或使用Maven执行
mvn exec:java -Dexec.mainClass="com.brianxiadong.lsmtree.LSMTreeExample"
```

### 3.5 运行测试

```bash
# 使用测试套件 (推荐)
./test-suite/test-suite.sh all

# 运行特定类型的测试
./test-suite/test-suite.sh functional    # 功能测试
./test-suite/test-suite.sh performance   # 性能测试
./test-suite/test-suite.sh memory        # 内存测试
./test-suite/test-suite.sh stress        # 压力测试

# 查看测试结果
./test-suite/test-suite.sh list          # 列出所有测试会话
./test-suite/test-suite.sh show latest   # 显示最新测试结果

# 传统 Maven 测试
mvn test

# 运行特定测试类
mvn test -Dtest=LSMTreeTest

# 运行性能测试
mvn test -Dtest=PerformanceTest
```

### 3.6 运行性能基准测试

#### 3.6.1 使用测试套件 (推荐)

```bash
# 运行性能基准测试
./test-suite/test-suite.sh performance

# 查看性能测试结果
./test-suite/test-suite.sh show latest
```

#### 3.6.2 使用 JUnit 测试

```bash
# 运行性能基准测试
mvn test -Dtest=BenchmarkTest

# 查看测试报告
open target/surefire-reports/TEST-com.brianxiadong.lsmtree.BenchmarkTest.xml
```

#### 3.6.3 直接运行基准测试

```bash
# 运行完整基准测试套件
java -cp target/java-lsm-tree-1.0.0.jar com.brianxiadong.lsmtree.BenchmarkRunner

# 使用自定义参数
java -cp target/java-lsm-tree-1.0.0.jar com.brianxiadong.lsmtree.BenchmarkRunner \
  --operations 50000 \
  --threads 4 \
  --key-size 32 \
  --value-size 200 \
  --data-dir ./benchmark_data
```

---

## 4. 文档索引

- 快速开始：`docs/quick-start.md`
- API 文档（OpenAPI）：`docs/api/openapi.yaml`
- 性能基准指南：`docs/performance/benchmark-baseline.md`

---

## 5. 性能基准测试

在现代硬件环境下的性能表现 (Java 8, SSD):

### 4.1 写入性能 (ops/sec)

| 测试类型 | 1K 数据量 | 5K 数据量 | 10K 数据量 | 50K 数据量 |
| -------- | --------- | --------- | ---------- | ---------- |
| 顺序写入 | 715,137   | 706,664   | 441,486    | 453,698    |
| 随机写入 | 303,479   | 573,723   | 393,951    | 453,400    |

### 4.2 读取性能 (ops/sec)

| 读取量 | 吞吐量 | 命中率 |
| ------ | ------ | ------ |
| 1,000  | 3,399  | 100%   |
| 5,000  | 3,475  | 100%   |
| 10,000 | 3,533  | 100%   |

### 4.3 混合工作负载 (70%读 + 30%写)

- **总操作数**: 20,000
- **整体吞吐量**: 4,473 ops/sec
- **读操作**: 14,092 (命中率: 100%)
- **写操作**: 5,908

### 4.4 延迟分布 (微秒)

- **平均延迟**: 1.8μs
- **中位数**: 1.3μs
- **P95**: 1.5μs
- **P99**: 1.9μs
- **最大延迟**: 4,248.3μs

### 4.5 批量加载性能

- **数据量**: 100,000 条记录
- **平均吞吐量**: 413,902 ops/sec
- **总耗时**: 241.60ms

### 4.6 MemTable 刷盘影响

- **正常场景**: ~400K ops/sec
- **频繁刷盘**: 72,210 ops/sec (MemTable 大小=100)
- **性能下降**: ~82% (由于频繁磁盘 I/O)

### 4.7 SSTable 性能特征

#### 4.7.1 SSTable 文件分析性能

| 文件大小 | 分析时间 | 条目数量 | 压缩率 |
| -------- | -------- | -------- | ------ |
| 1.2MB    | 15ms     | 10,000   | 无压缩 |
| 2.8MB    | 28ms     | 25,000   | 无压缩 |
| 5.1MB    | 45ms     | 50,000   | 无压缩 |

#### 4.7.2 范围查询性能

- **范围查询吞吐量**: 1,200-1,500 ops/sec (取决于范围大小)
- **平均范围查询延迟**: 800μs
- **最大范围查询延迟**: 2,500μs

#### 4.7.3 压缩性能

- **LZ4 压缩率**: ~60-70% (取决于数据模式)
- **压缩吞吐量**: 200-300 MB/s
- **解压吞吐量**: 500-800 MB/s

### 4.8 性能特征总结

✅ **写优化设计**: 写入性能达到 40 万 ops/sec 级别  
✅ **低延迟写入**: 平均 1.8 微秒，99%请求在 2 微秒内完成  
✅ **可预测性能**: 大数据量下性能保持稳定  
✅ **SSTable 分析高效**: 大文件分析时间在毫秒级别  
✅ **范围查询支持**: 支持高效的范围查询操作  
⚠️ **读性能权衡**: 读取性能约为写入的 1/100，符合 LSM Tree 特性  
⚠️ **压缩开销**: 压缩操作会增加额外的 CPU 开销

---

## 6. 使用指南

### 5.1 基本集成

#### 5.1.1 添加依赖

将项目作为依赖添加到你的 Maven 项目：

```xml
<dependency>
    <groupId>com.brianxiadong</groupId>
    <artifactId>lsm-tree</artifactId>
    <version>1.0.0</version>
</dependency>
```

或者直接下载源码：

```bash
git clone https://github.com/brianxiadong/java-lsm-tree.git
mvn clean install
```

#### 5.1.2 最简使用

```java
import com.brianxiadong.lsmtree.LSMTree;

public class QuickStart {
    public static void main(String[] args) throws Exception {
        // 创建LSM Tree (数据目录: "./data", MemTable最大1000条)
        try (LSMTree db = new LSMTree("./data", 1000)) {
            // 基础操作
            db.put("user:1001", "Alice");
            db.put("user:1002", "Bob");

            String user = db.get("user:1001"); // "Alice"
            db.delete("user:1002");

            System.out.println("用户信息: " + user);
        } // 自动关闭，释放资源
    }
}
```

### 5.2 配置优化

#### 5.2.1 性能调优参数

```java
// 根据应用场景调整MemTable大小
LSMTree highWriteDB = new LSMTree("./high_write", 50000);  // 高写入场景
LSMTree lowLatencyDB = new LSMTree("./low_latency", 1000); // 低延迟场景
LSMTree balancedDB = new LSMTree("./balanced", 10000);     // 平衡场景
```

#### 5.2.2 MemTable 大小选择指南

- **小 MemTable (1K-5K)**: 低内存占用，但频繁刷盘
- **中等 MemTable (10K-20K)**: 平衡内存和性能
- **大 MemTable (50K+)**: 高写入吞吐量，需要更多内存

### 5.3 实际应用场景

#### 5.3.1 缓存系统

```java
public class CacheService {
    private final LSMTree cache;

    public CacheService() throws IOException {
        this.cache = new LSMTree("./cache", 20000);
    }

    public void put(String key, String value, long ttl) throws IOException {
        // 添加TTL信息到value中
        String valueWithTTL = value + "|" + (System.currentTimeMillis() + ttl);
        cache.put(key, valueWithTTL);
    }

    public String get(String key) throws IOException {
        String value = cache.get(key);
        if (value == null) return null;

        // 检查TTL
        String[] parts = value.split("\\|");
        if (parts.length == 2) {
            long expiry = Long.parseLong(parts[1]);
            if (System.currentTimeMillis() > expiry) {
                cache.delete(key); // 过期删除
                return null;
            }
            return parts[0];
        }
        return value;
    }
}
```

#### 5.3.2 时序数据存储

```java
public class TimeSeriesDB {
    private final LSMTree tsdb;

    public TimeSeriesDB() throws IOException {
        this.tsdb = new LSMTree("./timeseries", 100000); // 大MemTable适合时序数据
    }

    public void recordMetric(String metric, double value) throws IOException {
        String key = metric + ":" + System.currentTimeMillis();
        tsdb.put(key, String.valueOf(value));
    }

    public void recordEvent(String event, String data) throws IOException {
        String key = "event:" + System.currentTimeMillis() + ":" + event;
        tsdb.put(key, data);
    }
}
```

#### 5.3.3 用户会话存储

```java
public class SessionStore {
    private final LSMTree sessions;

    public SessionStore() throws IOException {
        this.sessions = new LSMTree("./sessions", 10000);
    }

    public void createSession(String sessionId, String userId, Map<String, String> attributes) throws IOException {
        // 将属性序列化为JSON或简单格式
        StringBuilder value = new StringBuilder(userId);
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            value.append("|").append(entry.getKey()).append("=").append(entry.getValue());
        }
        sessions.put(sessionId, value.toString());
    }

    public String getSessionUser(String sessionId) throws IOException {
        String session = sessions.get(sessionId);
        return session != null ? session.split("\\|")[0] : null;
    }

    public void invalidateSession(String sessionId) throws IOException {
        sessions.delete(sessionId);
    }
}
```

### 5.4 监控和维护

#### 5.4.1 性能监控

```java
public void monitorPerformance(LSMTree db) throws IOException {
    // 定期获取统计信息
    LSMTree.LSMTreeStats stats = db.getStats();

    System.out.println("活跃MemTable条目: " + stats.getActiveMemTableSize());
    System.out.println("不可变MemTable数量: " + stats.getImmutableMemTableCount());
    System.out.println("SSTable文件数量: " + stats.getSsTableCount());

    // 监控指标
    if (stats.getSsTableCount() > 50) {
        System.out.println("警告: SSTable文件过多，考虑手动压缩");
    }

    if (stats.getActiveMemTableSize() > 0.8 * memTableMaxSize) {
        System.out.println("提示: MemTable即将满，准备刷盘");
    }
}
```

#### 5.4.2 手动维护操作

```java
public void maintenance(LSMTree db) throws IOException {
    // 强制刷盘 - 在关键时刻确保数据持久化
    db.flush();

    // 获取详细统计 - 用于性能调优
    LSMTree.LSMTreeStats stats = db.getStats();
    logStats(stats);
}

private void logStats(LSMTree.LSMTreeStats stats) {
    System.out.printf("LSM Tree状态 - 活跃: %d, 不可变: %d, SSTable: %d%n",
        stats.getActiveMemTableSize(),
        stats.getImmutableMemTableCount(),
        stats.getSsTableCount());
}
```

### 5.5 最佳实践

#### 5.5.1 错误处理

```java
public class SafeLSMWrapper {
    private LSMTree db;
    private final String dataDir;

    public SafeLSMWrapper(String dataDir, int memTableSize) {
        this.dataDir = dataDir;
        initDB(memTableSize);
    }

    private void initDB(int memTableSize) {
        try {
            this.db = new LSMTree(dataDir, memTableSize);
        } catch (IOException e) {
            System.err.println("LSM Tree初始化失败: " + e.getMessage());
            // 实现重试逻辑或使用备用方案
        }
    }

    public boolean safePut(String key, String value) {
        try {
            db.put(key, value);
            return true;
        } catch (IOException e) {
            System.err.println("写入失败: " + e.getMessage());
            return false;
        }
    }

    public String safeGet(String key) {
        try {
            return db.get(key);
        } catch (IOException e) {
            System.err.println("读取失败: " + e.getMessage());
            return null;
        }
    }
}
```

#### 5.5.2 资源管理

```java
// 推荐: 使用try-with-resources
try (LSMTree db = new LSMTree("./data", 10000)) {
    // 使用数据库
    performOperations(db);
} // 自动关闭

// 或手动管理
LSMTree db = null;
try {
    db = new LSMTree("./data", 10000);
    performOperations(db);
} finally {
    if (db != null) {
        try {
            db.close();
        } catch (IOException e) {
            System.err.println("关闭数据库失败: " + e.getMessage());
        }
    }
}
```

---

## 6. 文档指南

### 6.1 完整文档

- **[基准测试指南](docs/benchmark-guide.md)** - 详细的性能测试说明
- **[数据库分析工具](docs/db-analyzer-guide.md)** - SSTable 和 WAL 分析工具使用指南
- **[性能分析指南](docs/performance-analysis-guide.md)** - 性能优化和调试指南
- **[LSM Tree 深度解析](docs/lsm-tree-deep-dive.md)** - LSM Tree 架构和实现详解
- **[源码分析文档](docs/soucrce-code-analysis.md)** - 源码结构和设计分析
- **[测试套件使用说明](test-suite/README.md)** - 完整测试套件使用指南

### 6.2 分析工具

```bash
# SSTable 文件分析
./analyze-db.sh [选项] <SSTable文件路径>

# WAL 文件分析
./analyze-wal.sh [选项] <WAL文件路径>

# 查看工具帮助
./analyze-db.sh --help
./analyze-wal.sh --help
```

### 6.3 学习资源

- [教程目录](tutorials/) - 分步骤学习教程
- [学习计划](learn/) - 结构化学习计划和总结

### 6.4 快速链接

- **性能测试**: 使用 `BenchmarkRunner` 进行性能基准测试
- **数据分析**: 使用 `DatabaseAnalyzer` 分析数据库状态
- **性能调优**: 参考性能分析指南优化配置

---

## 7. 核心组件与实现细节

### 7.1 KeyValue

```java
// 基础数据结构，包含键、值、时间戳和删除标记
KeyValue kv = new KeyValue("key", "value");
KeyValue tombstone = KeyValue.createTombstone("key"); // 删除标记
```

### 7.2 MemTable

```java
// 内存中的有序表，基于跳表实现
MemTable memTable = new MemTable(1000);
memTable.put("key", "value");
String value = memTable.get("key");
```

### 7.3 SSTable

```java
// 磁盘上的有序文件
List<KeyValue> sortedData = Arrays.asList(/*...*/);
SSTable ssTable = new SSTable("data/table.db", sortedData);
String value = ssTable.get("key");
```

### 7.4 BloomFilter

```java
// 布隆过滤器，快速过滤不存在的键
BloomFilter filter = new BloomFilter(10000, 0.01);
filter.add("key");
boolean mightExist = filter.mightContain("key");
```

### 7.5 WAL (Write-Ahead Log)

```java
// 写前日志，确保数据持久性
WriteAheadLog wal = new WriteAheadLog("wal.log");
wal.append(WriteAheadLog.LogEntry.put("key", "value"));
List<WriteAheadLog.LogEntry> entries = wal.recover();
```

### 7.6 技术实现细节

#### 7.6.1 WAL 格式

```text
PUT|key|value|timestamp
DELETE|key||timestamp
```

#### 7.6.2 SSTable 文件格式

```text
[Entry Count: 4 bytes]
[Data Entries: Variable]
[Bloom Filter: Variable]
[Sparse Index: Variable]
```

#### 7.6.3 布隆过滤器实现

- 使用 Double Hashing 避免多个哈希函数
- 可配置误报率 (默认: 1%)
- 支持序列化/反序列化

#### 7.6.4 并发控制

- 使用 ReadWriteLock 实现读写分离
- 写操作互斥，读操作并发
- WAL 写入同步，确保持久性

---

## 8. 性能与配置

### 8.1 性能特征

#### 8.1.1 时间复杂度

- **写入**: O(log N) - MemTable 跳表插入
- **查询**: O(log N + K) - N 为 MemTable 大小，K 为 SSTable 数量
- **删除**: O(log N) - 插入删除标记

#### 8.1.2 空间复杂度

- **内存**: MemTable + 索引 + 布隆过滤器
- **磁盘**: SSTable 文件 + WAL 日志

#### 8.1.3 压缩策略

- **分层压缩**: Level-based compaction
- **触发条件**: 每层文件数量超过阈值
- **合并算法**: 多路归并排序 + 去重

### 8.2 配置参数

#### 8.2.1 LSMTree 构造参数

```java
LSMTree(String dataDir, int memTableMaxSize)
```

- `dataDir`: 数据存储目录
- `memTableMaxSize`: MemTable 最大条目数

#### 8.2.2 压缩策略配置

```java
CompactionStrategy(String dataDir, int maxLevelSize, int levelSizeMultiplier)
```

- `maxLevelSize`: Level 0 最大文件数 (默认: 4)
- `levelSizeMultiplier`: 级别大小倍数 (默认: 10)

---

## 9. 项目结构

```text
java-lsm-tree/
├── src/
│   ├── main/java/com/brianxiadong/lsmtree/
│   │   ├── LSMTree.java           # 主要的LSM Tree实现
│   │   ├── MemTable.java          # 内存表实现
│   │   ├── SSTable.java           # 排序字符串表实现
│   │   ├── BloomFilter.java       # 布隆过滤器实现
│   │   ├── WAL.java               # 预写日志实现
│   │   ├── Compaction.java        # 压缩策略实现
│   │   ├── BenchmarkRunner.java   # 性能基准测试工具
│   │   ├── RangeBenchmarkRunner.java # 范围查询性能测试工具
│   │   ├── tools/                 # 工具类目录
│   │   │   ├── SSTableAnalyzer.java      # SSTable 分析工具
│   │   │   ├── SSTableAnalyzerCLI.java   # SSTable 分析命令行工具
│   │   │   └── MetricsHttpServer.java    # 指标监控服务器
│   │   └── utils/                 # 工具类
│   └── test/java/                 # 测试代码
├── docs/                          # 完整文档
│   ├── lsm-tree-intro.md         # LSM Tree 介绍
│   ├── lsm-tree-deep-dive.md     # 深度技术解析
│   ├── benchmark-guide.md        # 基准测试指南
│   ├── db-analyzer-guide.md      # 数据库分析工具指南
│   ├── performance-analysis-guide.md # 性能分析指南
│   └── soucrce-code-analysis.md  # 源码分析
├── tutorials/                     # 学习教程
│   ├── README.md                 # 教程目录
│   ├── 01-lsm-tree-overview.md   # LSM Tree 概览
│   ├── 08-lsm-tree-main.md       # 核心实现教程
│   └── ...                       # 其他教程文件
├── learn/                         # 学习计划和总结
│   ├── learning-plan.md          # 学习计划
│   ├── 学习计划第一天完成总结.md    # 学习总结
│   ├── 学习计划第四天完成总结.md    # SSTable 实现总结
│   └── ...                       # 其他学习总结
├── test-suite/                    # 完整测试套件
│   ├── test-suite.sh             # 测试套件主脚本
│   ├── README.md                 # 测试套件说明
│   ├── common.sh                 # 通用函数库
│   ├── session.sh                # 会话管理
│   ├── lib/                      # 测试库函数
│   │   ├── tests.sh              # 测试函数库
│   │   ├── reports.sh            # 报告生成函数库
│   │   └── utils.sh              # 工具函数库
│   └── results/                  # 测试结果目录
├── analyze-db.sh                  # SSTable 分析工具
├── analyze-wal.sh                 # WAL 分析工具
├── build.sh                       # 构建脚本
├── pom.xml                        # Maven配置
└── README.md                      # 项目说明
```

---

## 10. 扩展功能

### 10.1 已实现

- [✓] 基础 CRUD 操作
- [✓] WAL 日志恢复
- [✓] 自动压缩
- [✓] 布隆过滤器优化
- [✓] 统计信息
- [✓] 并发安全
- [✓] Range 查询支持
- [✓] 数据压缩 (LZ4/None)
- [✓] SSTable 文件分析工具
- [✓] 性能基准测试框架
- [✓] 范围查询性能测试
- [✓] 指标监控服务器

### 10.2 计划中

- [ ] 更复杂的压缩策略 (Size-tiered/Leveled)
- [ ] 分区支持
- [ ] 事务支持
- [ ] 备份和恢复功能
- [ ] 分布式部署支持
- [ ] 更丰富的监控指标

## 11. 贡献

欢迎贡献代码！请遵循以下步骤：

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

---

## 12. 许可证

本项目采用 Apache 2.0 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情

---

## 13. 参考资料

- [The Log-Structured Merge-Tree (LSM-Tree)](http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.44.2782&rep=rep1&type=pdf)
- [LevelDB Documentation](https://github.com/google/leveldb/blob/main/doc/index.md)
- [RocksDB Wiki](https://github.com/facebook/rocksdb/wiki)

---

## 14. 作者

- **Brian Xia Dong** - [brianxiadong](https://github.com/brianxiadong)
- **Grissom Wang** - [Grissom Wang(AI 原力注入博主)](https://github.com/grissomsh)：本 forked repo 由 Grissom Wang 维护。

---

⭐ 如果这个项目对你有帮助，请给个 Star！
