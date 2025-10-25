# Java LSM Tree 源码解析文档

## 1. 项目概述

### 1.1 项目简介

本项目是一个基于 Java 实现的 Log-Structured Merge Tree (LSM Tree) 存储引擎，专为高并发写入场景设计。LSM Tree 是一种专门优化写入性能的数据结构，被广泛应用于现代分布式数据库系统中，如 LevelDB、RocksDB、Cassandra 和 HBase 等。

### 1.2 核心特性

- **高性能写入**：通过将随机写入转换为顺序写入，实现 O(log N) 的写入性能
- **数据持久化**：WAL (Write-Ahead Log) 确保数据的持久性和崩溃恢复能力
- **自动压缩**：后台自动执行 SSTable 合并，优化存储空间和查询性能
- **并发安全**：使用读写锁机制保证多线程环境下的数据一致性
- **空间优化**：布隆过滤器减少无效的磁盘 I/O 操作

### 1.3 技术架构

```text
写入流程: Write → WAL → MemTable → (满了) → SSTable
查询流程: MemTable → Immutable MemTables → SSTables (按时间倒序)
压缩流程: Level 0 → Level 1 → Level 2 → ... (分层压缩)
```

## 2. 整体架构设计

### 2.1 系统组件图

```text
┌─────────────────────────────────────────────────────────────┐
│                        LSM Tree                             │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │     WAL     │  │  MemTable   │  │  Immutable MemTable │  │
│  │ (Write-Ahead│  │  (Active)   │  │     (List)          │  │
│  │    Log)     │  │             │  │                     │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │   SSTable   │  │ BloomFilter │  │ CompactionStrategy  │  │
│  │  (Disk)     │  │             │  │                     │  │
│  │             │  │             │  │                     │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 数据流向

1. **写入路径**：数据首先写入 WAL，然后写入活跃的 MemTable
2. **刷盘路径**：当 MemTable 达到容量上限时，转为不可变状态并刷写到 SSTable
3. **查询路径**：按照数据新旧程度依次查询：活跃 MemTable → 不可变 MemTable → SSTable
4. **压缩路径**：后台线程定期执行 SSTable 合并，清理冗余数据

## 3. 核心类详细解析

### 3.1 KeyValue 类

#### 3.1.1 设计目的

`KeyValue` 类是 LSM Tree 的基础数据单元，封装了键值对及其元数据信息。

#### 3.1.2 核心字段

```java
public class KeyValue implements Comparable<KeyValue> {
    private final String key;        // 键
    private final String value;      // 值
    private final long timestamp;    // 时间戳，用于版本控制
    private final boolean deleted;   // 删除标记（墓碑标记）
}
```

#### 3.1.3 关键特性

- **时间版本控制**：通过时间戳实现同一键的多版本管理
- **删除语义**：使用墓碑标记 (Tombstone) 实现逻辑删除
- **排序支持**：实现 `Comparable` 接口，支持按键和时间戳排序

#### 3.1.4 排序规则

```java
@Override
public int compareTo(KeyValue other) {
    int keyCompare = this.key.compareTo(other.key);
    if (keyCompare != 0) {
        return keyCompare;
    }
    // 如果键相同，按时间戳降序排列（新的在前）
    return Long.compare(other.timestamp, this.timestamp);
}
```

### 3.2 MemTable 类

#### 3.2.1 设计目的

`MemTable` 是内存中的有序数据结构，作为写入操作的缓冲区，将随机写入转换为顺序写入。

#### 3.2.2 底层实现

使用 `ConcurrentSkipListMap` 作为底层数据结构：

```java
private final ConcurrentSkipListMap<String, KeyValue> data;
```

#### 3.2.3 选择跳表的原因

| 数据结构 | 插入 | 查找 | 删除 | 有序遍历 | 并发性能 |
|----------|------|------|------|----------|----------|
| 跳表     | O(log N) | O(log N) | O(log N) | O(N) | 优秀 |
| 红黑树   | O(log N) | O(log N) | O(log N) | O(N) | 一般 |
| 哈希表   | O(1) | O(1) | O(1) | 不支持 | 优秀 |

#### 3.2.4 核心方法

- **put(String key, String value)**：插入键值对
- **delete(String key)**：插入删除标记
- **get(String key)**：查询键值
- **shouldFlush()**：检查是否需要刷盘
- **getAllEntries()**：获取所有条目用于刷盘

### 3.3 SSTable 类

#### 3.3.1 设计目的

`SSTable` (Sorted String Table) 是磁盘上的有序不可变文件，用于持久化存储数据。

#### 3.3.2 文件格式

```text
┌─────────────────┐
│   条目数量 (4B)  │
├─────────────────┤
│   键 (UTF-8)    │
│   删除标记 (1B)  │
│   值 (UTF-8)    │  ← 重复 N 次
│   时间戳 (8B)   │
├─────────────────┤
│      ...        │
└─────────────────┘
```

#### 3.3.3 核心特性

- **不可变性**：一旦创建，文件内容不可修改
- **有序性**：数据按键的字典序排列
- **布隆过滤器**：每个 SSTable 都有对应的布隆过滤器
- **压缩友好**：支持与其他 SSTable 合并

#### 3.3.4 查询优化

```java
public String get(String key) {
    // 首先检查布隆过滤器
    if (!bloomFilter.mightContain(key)) {
        return null;  // 确定不存在
    }
    
    // 在文件中顺序搜索
    // 由于数据有序，可以提前终止搜索
}
```

### 3.4 WriteAheadLog 类

#### 3.4.1 设计目的

WAL 确保数据的持久性和崩溃恢复能力，是 LSM Tree 数据可靠性的重要保障。

#### 3.4.2 日志格式

| 操作类型 | 键 | 值 | 时间戳 |
|----------|----|----|--------|
| PUT | user:1 | Alice | 1640995200000 |
| DELETE | user:2 |  | 1640995201000 |

#### 3.4.3 核心操作

- **append(LogEntry entry)**：追加日志条目
- **checkpoint()**：清理已刷盘的日志
- **recover()**：从日志恢复数据

#### 3.4.4 崩溃恢复流程

1. 读取 WAL 文件中的所有日志条目
2. 按时间顺序重放操作到 MemTable
3. 清理已恢复的日志

### 3.5 BloomFilter 类

#### 3.5.1 设计目的

布隆过滤器是一种概率型数据结构，用于快速判断键是否可能存在于 SSTable 中，显著减少无效的磁盘 I/O。

#### 3.5.2 核心特性

- **无假阴性**：如果布隆过滤器说元素不存在，那么元素一定不存在
- **有假阳性**：如果布隆过滤器说元素存在，元素可能不存在
- **空间高效**：相比哈希表，内存占用极小

#### 3.5.3 参数计算

```java
// 计算最优位数组大小
this.size = (int) (-expectedElements * Math.log(falsePositiveProbability)
        / (Math.log(2) * Math.log(2)));

// 计算最优哈希函数个数
this.hashFunctions = (int) (size * Math.log(2) / expectedElements);
```

#### 3.5.4 多重哈希实现

使用 Double Hashing 技术避免实现多个独立的哈希函数：

```java
private int hash(String key, int i) {
    int hash1 = key.hashCode();
    int hash2 = hash1 >>> 16;
    return hash1 + i * hash2;
}
```

### 3.6 CompactionStrategy 类

#### 3.6.1 设计目的

压缩策略负责 SSTable 的合并和优化，是 LSM Tree 性能优化的核心机制。

#### 3.6.2 压缩触发条件

- SSTable 文件数量超过阈值
- 某层文件大小超过限制
- 读放大过大时
- 定期压缩任务

#### 3.6.3 分层压缩策略

```text
Level 0: [SSTable] [SSTable] [SSTable] [SSTable]  (4个文件时触发压缩)
Level 1: [SSTable] [SSTable] ... (40个文件时触发压缩)  
Level 2: [SSTable] [SSTable] ... (400个文件时触发压缩)
```

#### 3.6.4 合并去重算法

```java
private List<KeyValue> mergeAndDedup(List<KeyValue> entries) {
    // 1. 按键和时间戳排序
    entries.sort(KeyValue::compareTo);
    
    // 2. 保留每个键的最新版本
    Map<String, KeyValue> latestEntries = new HashMap<>();
    for (KeyValue entry : entries) {
        String key = entry.getKey();
        if (!latestEntries.containsKey(key) ||
                entry.getTimestamp() > latestEntries.get(key).getTimestamp()) {
            latestEntries.put(key, entry);
        }
    }
    
    // 3. 移除删除标记的过期条目
    List<KeyValue> dedupedEntries = new ArrayList<>();
    for (KeyValue entry : latestEntries.values()) {
        if (!entry.isDeleted()) {
            dedupedEntries.add(entry);
        }
    }
    
    return dedupedEntries;
}
```

### 3.7 LSMTree 主类

#### 3.7.1 设计目的

`LSMTree` 类是整个存储引擎的控制中心，协调各个组件的工作。

#### 3.7.2 核心组件

```java
public class LSMTree implements AutoCloseable {
    // 内存组件
    private volatile MemTable activeMemTable;           // 活跃 MemTable
    private final List<MemTable> immutableMemTables;    // 不可变 MemTable 列表
    
    // 磁盘组件
    private final List<SSTable> ssTables;               // SSTable 文件列表
    
    // 后台任务
    private final ExecutorService compactionExecutor;   // 压缩线程池
    private final CompactionStrategy compactionStrategy; // 压缩策略
    
    // 持久化组件
    private final WriteAheadLog wal;                    // WAL 实例
    
    // 并发控制
    private final ReadWriteLock lock;                   // 读写锁
}
```

#### 3.7.3 写入流程

```java
public void put(String key, String value) throws IOException {
    lock.writeLock().lock();
    try {
        // 1. 写入 WAL
        wal.append(WriteAheadLog.LogEntry.put(key, value));
        
        // 2. 写入活跃 MemTable
        activeMemTable.put(key, value);
        
        // 3. 检查是否需要刷盘
        if (activeMemTable.shouldFlush()) {
            flushMemTable();
        }
    } finally {
        lock.writeLock().unlock();
    }
}
```

#### 3.7.4 查询流程

```java
public String get(String key) {
    lock.readLock().lock();
    try {
        // 1. 查询活跃 MemTable
        String value = activeMemTable.get(key);
        if (value != null) return value;
        
        // 2. 查询不可变 MemTable（按时间倒序）
        for (int i = immutableMemTables.size() - 1; i >= 0; i--) {
            value = immutableMemTables.get(i).get(key);
            if (value != null) return value;
        }
        
        // 3. 查询 SSTable（按创建时间倒序）
        List<SSTable> sortedSSTables = new ArrayList<>(ssTables);
        sortedSSTables.sort((a, b) -> 
            Long.compare(b.getCreationTime(), a.getCreationTime()));
        
        for (SSTable ssTable : sortedSSTables) {
            value = ssTable.get(key);
            if (value != null) return value;
        }
        
        return null;
    } finally {
        lock.readLock().unlock();
    }
}
```

## 4. 关键算法与数据结构

### 4.1 跳表 (Skip List)

#### 4.1.1 原理

跳表是一种概率性数据结构，通过多层索引实现快速查找。

```text
Level 3: 1 -----------------> 9
Level 2: 1 -------> 5 ------> 9
Level 1: 1 -> 3 -> 5 -> 7 -> 9
Level 0: 1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7 -> 8 -> 9
```

#### 4.1.2 优势

- **查找效率**：平均时间复杂度 O(log N)
- **插入删除**：平均时间复杂度 O(log N)
- **并发友好**：相比红黑树更适合并发操作
- **有序遍历**：天然支持有序遍历

### 4.2 布隆过滤器算法

#### 4.2.1 工作原理

1. 使用 k 个独立的哈希函数
2. 将元素映射到位数组的 k 个位置
3. 查询时检查这 k 个位置是否都为 1

#### 4.2.2 参数优化

- **位数组大小**：m = -n * ln(p) / (ln(2))²
- **哈希函数个数**：k = (m/n) * ln(2)
- **假阳性概率**：p = (1 - e^(-kn/m))^k

其中 n 是预期元素数量，p 是期望的假阳性概率。

### 4.3 LSM Tree 合并算法

#### 4.3.1 多路归并

```java
// 伪代码
List<Iterator<KeyValue>> iterators = getIterators(ssTables);
PriorityQueue<KeyValue> minHeap = new PriorityQueue<>();

// 初始化堆
for (Iterator<KeyValue> iter : iterators) {
    if (iter.hasNext()) {
        minHeap.offer(iter.next());
    }
}

// 归并过程
while (!minHeap.isEmpty()) {
    KeyValue min = minHeap.poll();
    output.add(min);
    
    // 添加下一个元素
    if (correspondingIterator.hasNext()) {
        minHeap.offer(correspondingIterator.next());
    }
}
```

#### 4.3.2 去重策略

- **时间戳优先**：保留时间戳最新的版本
- **删除标记处理**：清理过期的删除标记
- **空间回收**：移除被覆盖的旧版本数据

## 5. 性能特征与优化

### 5.1 写入性能

#### 5.1.1 性能优势

- **顺序写入**：将随机写入转换为顺序写入，充分利用磁盘顺序 I/O 性能
- **批量刷盘**：MemTable 达到阈值后批量写入，减少磁盘 I/O 次数
- **WAL 优化**：使用缓冲写入和强制刷盘保证持久性

#### 5.1.2 写入路径优化

```text
内存写入 (ns级) → WAL写入 (μs级) → 批量刷盘 (ms级)
```

### 5.2 读取性能

#### 5.2.1 查询优化策略

- **布隆过滤器**：快速过滤不存在的键，减少磁盘 I/O
- **分层查询**：按数据新旧程度查询，新数据优先
- **缓存友好**：MemTable 常驻内存，热数据访问快速

#### 5.2.2 读放大问题

LSM Tree 的读放大主要来源于：

- 需要查询多个 SSTable 文件
- 布隆过滤器的假阳性
- 压缩不及时导致的文件数量过多

### 5.3 空间放大

#### 5.3.1 空间放大来源

- **多版本数据**：同一键的多个版本同时存在
- **删除标记**：墓碑标记占用额外空间
- **压缩延迟**：压缩不及时导致冗余数据

#### 5.3.2 优化策略

- **及时压缩**：根据文件数量和大小触发压缩
- **TTL 机制**：为数据设置生存时间
- **分层策略**：不同层级使用不同的压缩策略

### 5.4 并发性能

#### 5.4.1 并发控制机制

- **读写锁**：读操作并发，写操作互斥
- **无锁数据结构**：MemTable 使用 ConcurrentSkipListMap
- **后台压缩**：压缩操作在后台异步执行

#### 5.4.2 锁粒度优化

```java
// 读操作使用读锁，允许并发
lock.readLock().lock();
try {
    // 查询操作
} finally {
    lock.readLock().unlock();
}

// 写操作使用写锁，保证一致性
lock.writeLock().lock();
try {
    // 写入和刷盘操作
} finally {
    lock.writeLock().unlock();
}
```

## 6. 系统可靠性

### 6.1 数据持久性

#### 6.1.1 WAL 机制

- **写前日志**：数据写入 MemTable 前先写入 WAL
- **强制刷盘**：使用 `flush()` 确保数据写入磁盘
- **原子操作**：单个操作要么完全成功，要么完全失败

#### 6.1.2 崩溃恢复

```java
private void recover() throws IOException {
    // 1. 恢复 SSTable
    loadExistingSSTables();
    
    // 2. 从 WAL 恢复未刷盘的数据
    List<WriteAheadLog.LogEntry> entries = wal.recover();
    for (WriteAheadLog.LogEntry entry : entries) {
        replayLogEntry(entry);
    }
}
```

### 6.2 数据一致性

#### 6.2.1 ACID 特性

- **原子性**：单个操作的原子性通过 WAL 保证
- **一致性**：通过读写锁保证数据的一致性视图
- **隔离性**：读写锁提供快照隔离级别
- **持久性**：WAL 和 SSTable 保证数据持久性

#### 6.2.2 并发一致性

- **读写分离**：读操作不阻塞写操作
- **版本控制**：通过时间戳实现多版本并发控制
- **原子刷盘**：MemTable 到 SSTable 的转换是原子的

## 7. 使用示例与最佳实践

### 7.1 基本使用

```java
// 创建 LSM Tree 实例
try (LSMTree lsmTree = new LSMTree("data", 1000)) {
    
    // 插入数据
    lsmTree.put("user:1", "Alice");
    lsmTree.put("user:2", "Bob");
    
    // 查询数据
    String value = lsmTree.get("user:1");
    System.out.println("user:1 = " + value);
    
    // 更新数据
    lsmTree.put("user:1", "Alice Updated");
    
    // 删除数据
    lsmTree.delete("user:2");
    
    // 强制刷盘
    lsmTree.flush();
}
```

### 7.2 性能调优

#### 7.2.1 MemTable 大小调优

```java
// 较大的 MemTable 可以减少刷盘频率，但会增加内存使用
LSMTree lsmTree = new LSMTree("data", 10000); // 10K 条目
```

#### 7.2.2 压缩策略调优

```java
// 调整压缩触发阈值
CompactionStrategy strategy = new CompactionStrategy(
    "data",     // 数据目录
    4,          // Level 0 最大文件数
    10          // 层级大小倍数
);
```

### 7.3 监控与诊断

#### 7.3.1 统计信息

```java
LSMTreeStats stats = lsmTree.getStats();
System.out.println("Active MemTable size: " + stats.getActiveMemTableSize());
System.out.println("Immutable MemTables: " + stats.getImmutableMemTableCount());
System.out.println("SSTable count: " + stats.getSsTableCount());
```

#### 7.3.2 性能指标

- **写入吞吐量**：每秒写入操作数
- **读取延迟**：查询操作的平均延迟
- **空间放大**：实际存储空间与逻辑数据大小的比值
- **读放大**：单次查询需要读取的 SSTable 数量

## 8. 扩展与改进

### 8.1 可能的优化方向

#### 8.1.1 索引优化

- **稀疏索引**：为 SSTable 添加稀疏索引，加速查找
- **二分查找**：在 SSTable 中使用二分查找替代顺序查找
- **缓存机制**：添加 LRU 缓存提高热数据访问性能

#### 8.1.2 压缩优化

- **增量压缩**：只压缩变化的部分，减少 I/O
- **并行压缩**：多线程并行执行压缩任务
- **智能调度**：根据系统负载动态调整压缩策略

#### 8.1.3 存储优化

- **数据压缩**：使用 Snappy、LZ4 等算法压缩数据
- **列式存储**：对于结构化数据，使用列式存储格式
- **分区策略**：按时间或键范围分区，提高查询效率

### 8.2 生产环境考虑

#### 8.2.1 可靠性增强

- **副本机制**：多副本保证数据可靠性
- **校验和**：数据完整性校验
- **备份恢复**：定期备份和快速恢复机制

#### 8.2.2 运维支持

- **监控指标**：详细的性能和健康状况监控
- **日志记录**：完善的日志记录和错误处理
- **配置管理**：灵活的配置参数和热更新支持

## 9. 总结

### 9.1 技术亮点

本 Java LSM Tree 实现具有以下技术亮点：

1. **完整的 LSM Tree 架构**：包含 MemTable、SSTable、WAL、布隆过滤器和压缩策略等所有核心组件
2. **高性能设计**：使用跳表、布隆过滤器等高效数据结构，优化读写性能
3. **并发安全**：通过读写锁和无锁数据结构保证多线程环境下的数据一致性
4. **数据可靠性**：WAL 机制确保数据持久性和崩溃恢复能力
5. **代码质量**：清晰的代码结构、完善的注释和良好的编程实践

### 9.2 适用场景

- **写密集型应用**：日志系统、监控数据、时序数据库
- **大数据存储**：分布式数据库的存储引擎
- **缓存系统**：持久化缓存的底层存储
- **学习研究**：理解 LSM Tree 原理和实现细节

### 9.3 学习价值

通过分析本项目的源码，可以深入理解：

- LSM Tree 的设计原理和实现细节
- 高性能存储引擎的架构设计
- 并发编程和数据一致性保证
- 系统性能优化的方法和技巧
- 生产级代码的编写规范和最佳实践

本文档详细分析了 Java LSM Tree 实现的各个方面，从整体架构到具体实现，从性能优化到可靠性保证，为理解和使用 LSM Tree 提供了全面的技术参考。
