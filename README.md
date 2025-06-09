# Java LSM Tree 实现

![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)
![Java](https://img.shields.io/badge/java-8+-orange.svg)
![Maven](https://img.shields.io/badge/maven-3.6+-green.svg)

一个用Java实现的Log-Structured Merge Tree (LSM Tree)数据结构，包含所有LSM Tree的核心特性。

## 特性

### 核心LSM Tree组件
- **MemTable**: 内存中的有序数据结构，使用跳表实现
- **SSTable**: 磁盘上的有序不可变文件
- **WAL (Write-Ahead Log)**: 写前日志，确保数据持久性
- **布隆过滤器**: 快速判断键是否可能存在
- **压缩策略**: 多级合并压缩，优化存储和查询性能

### 主要功能
- ✅ **高性能写入**: O(log N) 写入性能
- ✅ **高效查询**: 结合内存和磁盘的多层查询
- ✅ **数据持久化**: WAL确保崩溃恢复
- ✅ **自动压缩**: 后台自动执行SSTable合并
- ✅ **并发安全**: 读写锁保证线程安全
- ✅ **空间优化**: 布隆过滤器减少无效磁盘IO

## 架构设计

### LSM Tree结构
```
写入流程: Write -> WAL -> MemTable -> (满了) -> SSTable
查询流程: MemTable -> Immutable MemTables -> SSTables (按时间倒序)
```

### 分层压缩
```
Level 0: [SSTable] [SSTable] [SSTable] [SSTable]  (4个文件时触发压缩)
Level 1: [SSTable] [SSTable] ... (40个文件时触发压缩)  
Level 2: [SSTable] [SSTable] ... (400个文件时触发压缩)
...
```

## 快速开始

### 环境要求
- Java 8 或更高版本
- Maven 3.6 或更高版本

### 安装和构建
```bash
git clone https://github.com/brianxiadong/java-lsm-tree.git
cd java-lsm-tree
mvn clean compile
```

### 基本使用

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

### 运行示例
```bash
mvn exec:java -Dexec.mainClass="com.brianxiadong.lsmtree.LSMTreeExample"
```

### 运行测试
```bash
mvn test
```

### 运行性能基准测试
```bash
# 使用JUnit基准测试
mvn test -Dtest=LSMTreeBenchmark

# 或直接运行基准测试程序
mvn exec:java -Dexec.mainClass="com.brianxiadong.lsmtree.BenchmarkRunner"
```

## 性能基准测试

在现代硬件环境下的性能表现 (Java 8, SSD):

### 写入性能 (ops/sec)
| 测试类型 | 1K数据量 | 5K数据量 | 10K数据量 | 50K数据量 |
|---------|---------|---------|----------|----------|
| 顺序写入 | 715,137 | 706,664 | 441,486  | 453,698  |
| 随机写入 | 303,479 | 573,723 | 393,951  | 453,400  |

### 读取性能 (ops/sec)
| 读取量 | 吞吐量 | 命中率 |
|--------|-------|--------|
| 1,000  | 3,399 | 100%   |
| 5,000  | 3,475 | 100%   |
| 10,000 | 3,533 | 100%   |

### 混合工作负载 (70%读 + 30%写)
- **总操作数**: 20,000
- **整体吞吐量**: 4,473 ops/sec  
- **读操作**: 14,092 (命中率: 100%)
- **写操作**: 5,908

### 延迟分布 (微秒)
- **平均延迟**: 1.8μs
- **中位数**: 1.3μs  
- **P95**: 1.5μs
- **P99**: 1.9μs
- **最大延迟**: 4,248.3μs

### 批量加载性能
- **数据量**: 100,000 条记录
- **平均吞吐量**: 413,902 ops/sec
- **总耗时**: 241.60ms

### MemTable刷盘影响
- **正常场景**: ~400K ops/sec
- **频繁刷盘**: 72,210 ops/sec (MemTable大小=100)
- **性能下降**: ~82% (由于频繁磁盘I/O)

### 性能特征总结
✅ **写优化设计**: 写入性能达到40万ops/sec级别  
✅ **低延迟写入**: 平均1.8微秒，99%请求在2微秒内完成  
✅ **可预测性能**: 大数据量下性能保持稳定  
⚠️ **读性能权衡**: 读取性能约为写入的1/100，符合LSM Tree特性  

## 使用指南

### 1. 基本集成

#### 添加依赖
将项目作为依赖添加到你的Maven项目：

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

#### 最简使用
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

### 2. 配置优化

#### 性能调优参数
```java
// 根据应用场景调整MemTable大小
LSMTree highWriteDB = new LSMTree("./high_write", 50000);  // 高写入场景
LSMTree lowLatencyDB = new LSMTree("./low_latency", 1000); // 低延迟场景
LSMTree balancedDB = new LSMTree("./balanced", 10000);     // 平衡场景
```

#### MemTable大小选择指南
- **小MemTable (1K-5K)**: 低内存占用，但频繁刷盘
- **中等MemTable (10K-20K)**: 平衡内存和性能
- **大MemTable (50K+)**: 高写入吞吐量，需要更多内存

### 3. 实际应用场景

#### 缓存系统
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

#### 时序数据存储
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

#### 用户会话存储
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

### 4. 监控和维护

#### 性能监控
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

#### 手动维护操作
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

### 5. 最佳实践

#### 错误处理
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

#### 资源管理
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

## 核心组件详解

### 1. KeyValue
```java
// 基础数据结构，包含键、值、时间戳和删除标记
KeyValue kv = new KeyValue("key", "value");
KeyValue tombstone = KeyValue.createTombstone("key"); // 删除标记
```

### 2. MemTable
```java
// 内存中的有序表，基于跳表实现
MemTable memTable = new MemTable(1000);
memTable.put("key", "value");
String value = memTable.get("key");
```

### 3. SSTable
```java
// 磁盘上的有序文件
List<KeyValue> sortedData = Arrays.asList(/*...*/);
SSTable ssTable = new SSTable("data/table.db", sortedData);
String value = ssTable.get("key");
```

### 4. BloomFilter
```java
// 布隆过滤器，快速过滤不存在的键
BloomFilter filter = new BloomFilter(10000, 0.01);
filter.add("key");
boolean mightExist = filter.mightContain("key");
```

### 5. WAL (Write-Ahead Log)
```java
// 写前日志，确保数据持久性
WriteAheadLog wal = new WriteAheadLog("wal.log");
wal.append(WriteAheadLog.LogEntry.put("key", "value"));
List<WriteAheadLog.LogEntry> entries = wal.recover();
```

## 性能特征

### 时间复杂度
- **写入**: O(log N) - MemTable跳表插入
- **查询**: O(log N + K) - N为MemTable大小，K为SSTable数量
- **删除**: O(log N) - 插入删除标记

### 空间复杂度
- **内存**: MemTable + 索引 + 布隆过滤器
- **磁盘**: SSTable文件 + WAL日志

### 压缩策略
- **分层压缩**: Level-based compaction
- **触发条件**: 每层文件数量超过阈值
- **合并算法**: 多路归并排序 + 去重

## 配置参数

### LSMTree构造参数
```java
LSMTree(String dataDir, int memTableMaxSize)
```
- `dataDir`: 数据存储目录
- `memTableMaxSize`: MemTable最大条目数

### 压缩策略配置
```java
CompactionStrategy(String dataDir, int maxLevelSize, int levelSizeMultiplier)
```
- `maxLevelSize`: Level 0最大文件数 (默认: 4)
- `levelSizeMultiplier`: 级别大小倍数 (默认: 10)

## 项目结构

```
src/
├── main/java/com/brianxiadong/lsmtree/
│   ├── LSMTree.java              # 主要LSM Tree实现
│   ├── KeyValue.java             # 键值对数据结构
│   ├── MemTable.java             # 内存表
│   ├── SSTable.java              # 磁盘表
│   ├── BloomFilter.java          # 布隆过滤器
│   ├── WriteAheadLog.java        # 写前日志
│   ├── CompactionStrategy.java   # 压缩策略
│   └── LSMTreeExample.java       # 使用示例
└── test/java/com/brianxiadong/lsmtree/
    └── LSMTreeTest.java          # 单元测试
```

## 技术细节

### WAL格式
```
PUT|key|value|timestamp
DELETE|key||timestamp
```

### SSTable文件格式
```
[Entry Count: 4 bytes]
[Data Entries: Variable]
[Bloom Filter: Variable]
[Sparse Index: Variable]
```

### 布隆过滤器
- 使用Double Hashing避免多个哈希函数
- 可配置误报率 (默认: 1%)
- 支持序列化/反序列化

### 并发控制
- 使用ReadWriteLock实现读写分离
- 写操作互斥，读操作并发
- WAL写入同步，确保持久性

## 扩展功能

### 已实现
- [x] 基础CRUD操作
- [x] WAL日志恢复
- [x] 自动压缩
- [x] 布隆过滤器优化
- [x] 统计信息
- [x] 并发安全

### 计划中
- [ ] Range查询支持
- [ ] 数据压缩 (Snappy/LZ4)
- [ ] 更复杂的压缩策略
- [ ] 监控和度量
- [ ] 分区支持

## 贡献

欢迎贡献代码！请遵循以下步骤：

1. Fork项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建Pull Request

## 许可证

本项目采用Apache 2.0许可证 - 查看 [LICENSE](LICENSE) 文件了解详情

## 参考资料

- [The Log-Structured Merge-Tree (LSM-Tree)](http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.44.2782&rep=rep1&type=pdf)
- [LevelDB Documentation](https://github.com/google/leveldb/blob/main/doc/index.md)
- [RocksDB Wiki](https://github.com/facebook/rocksdb/wiki)

## 作者

**Brian Xia Dong** - [brianxiadong](https://github.com/brianxiadong)

---

⭐ 如果这个项目对你有帮助，请给个Star！ 