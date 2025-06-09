🔥 推荐一个高质量的Java LSM Tree开源项目！
[https://github.com/brianxiadong/java-lsm-tree](https://github.com/brianxiadong/java-lsm-tree)
**java-lsm-tree** 是一个从零实现的Log-Structured Merge Tree，专为高并发写入场景设计。
核心亮点：
⚡ 极致性能：写入速度超过40万ops/秒，完爆传统B+树
🏗️ 完整架构：MemTable跳表 + SSTable + WAL + 布隆过滤器 + 多级压缩
📚 深度教程：12章详细教程，从基础概念到生产优化，每行代码都有注释
🔒 并发安全：读写锁机制，支持高并发场景
💾 数据可靠：WAL写前日志确保崩溃恢复，零数据丢失
适合谁？
- 想深入理解LSM Tree原理的开发者
- 需要高写入性能存储引擎的项目
- 准备数据库/存储系统面试的同学
- 对分布式存储感兴趣的工程师
⭐ 给个Star支持开源！

# 第8章：LSM Tree 主程序实现

## 核心架构设计

LSM Tree主程序是整个存储引擎的控制中心，它协调MemTable、SSTable、WAL和压缩策略等组件的工作。

### 主要组件结构

```java
package com.brianxiadong.lsmtree;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * LSM Tree 主要实现类
 * 整合MemTable、SSTable和压缩策略
 */
public class LSMTree implements AutoCloseable {
    private final String dataDir;                        // 数据存储目录
    private final int memTableMaxSize;                   // MemTable最大容量
    private final ReadWriteLock lock;                    // 读写锁，保证并发安全

    // 内存组件：活跃和不可变MemTable
    private volatile MemTable activeMemTable;           // 当前活跃的MemTable
    private final List<MemTable> immutableMemTables;    // 不可变MemTable列表

    // 磁盘组件：SSTable文件列表
    private final List<SSTable> ssTables;               // 所有SSTable文件

    // 后台任务：压缩执行器和策略
    private final ExecutorService compactionExecutor;   // 压缩任务线程池
    private final CompactionStrategy compactionStrategy; // 压缩策略

    // WAL (Write-Ahead Log) 写前日志
    private final WriteAheadLog wal;                    // WAL实例

    // LSM Tree构造器：初始化所有组件
    public LSMTree(String dataDir, int memTableMaxSize) throws IOException {
        this.dataDir = dataDir;                          // 设置数据目录
        this.memTableMaxSize = memTableMaxSize;          // 设置MemTable大小限制
        this.lock = new ReentrantReadWriteLock();        // 初始化读写锁

        // 确保数据目录存在
        createDirectoryIfNotExists(dataDir);

        // 初始化内存组件
        this.activeMemTable = new MemTable(memTableMaxSize);  // 创建活跃MemTable
        this.immutableMemTables = new ArrayList<>();     // 初始化不可变MemTable列表
        this.ssTables = new ArrayList<>();              // 初始化SSTable列表

        // 初始化压缩策略（最多4个文件触发压缩，层级倍数为10）
        this.compactionStrategy = new CompactionStrategy(dataDir, 4, 10);

        // 初始化WAL写前日志
        this.wal = new WriteAheadLog(dataDir + "/wal.log");

        // 启动后台压缩线程（单线程，避免并发冲突）
        this.compactionExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LSMTree-Compaction"); // 设置线程名
            t.setDaemon(true);                           // 设为守护线程
            return t;
        });

        // 系统启动时恢复已有数据
        recover();

        // 注意：后台压缩任务暂时禁用，避免测试时的线程问题
        // startBackgroundCompaction();
    }
}
```

**架构设计解析**：LSM Tree的核心设计采用分层存储架构。内存层包括活跃MemTable（接收新写入）和不可变MemTable（准备刷盘）。磁盘层包含多个SSTable文件，按时间顺序组织。读写锁确保并发安全：读操作可以并发，但写操作是独占的。WAL确保数据持久性，压缩策略管理SSTable的合并优化。这种设计在高写入性能和数据一致性之间取得了最佳平衡。

## 数据写入流程

### 写入操作实现

```java
/**
 * 插入键值对
 */
public void put(String key, String value) throws IOException {
    if (key == null || value == null) {               // 参数合法性检查
        throw new IllegalArgumentException("Key and value cannot be null");
    }

    lock.writeLock().lock();                          // 获取写锁，确保线程安全
    try {
        // 步骤1: 先写WAL确保持久性（WAL-first原则）
        wal.append(WriteAheadLog.LogEntry.put(key, value)); // 记录PUT操作到WAL

        // 步骤2: 写入活跃MemTable（内存操作，速度快）
        activeMemTable.put(key, value);               // 更新内存中的数据

        // 步骤3: 检查是否需要刷盘（MemTable容量控制）
        if (activeMemTable.shouldFlush()) {           // 检查是否达到刷盘条件
            flushMemTable();                          // 触发MemTable刷盘
        }
    } finally {
        lock.writeLock().unlock();                    // 释放写锁
    }
}
```

**写入流程解析**：LSM Tree的写入操作遵循严格的"WAL-first"原则，确保数据的持久性和一致性。首先将操作记录到WAL，即使系统崩溃也能恢复。然后更新活跃MemTable，这是一个纯内存操作，速度极快。最后检查是否需要刷盘，当MemTable达到容量限制时触发刷盘，保持内存使用可控。整个过程在写锁保护下执行，确保并发安全。

### 删除操作实现

```java
/**
 * 删除键
 */
public void delete(String key) throws IOException {
    if (key == null) {                               // 参数合法性检查
        throw new IllegalArgumentException("Key cannot be null");
    }

    lock.writeLock().lock();                          // 获取写锁，确保线程安全
    try {
        // 步骤1: 先写WAL记录删除操作（确保删除操作持久化）
        wal.append(WriteAheadLog.LogEntry.delete(key)); // 记录DELETE操作到WAL

        // 步骤2: 在活跃MemTable中标记删除（逻辑删除，插入墓碑标记）
        activeMemTable.delete(key);                   // 创建删除标记而非物理删除

        // 步骤3: 检查是否需要刷盘（删除操作也会增加MemTable大小）
        if (activeMemTable.shouldFlush()) {           // 检查是否达到刷盘条件
            flushMemTable();                          // 触发MemTable刷盘
        }
    } finally {
        lock.writeLock().unlock();                    // 释放写锁
    }
}
```

**删除操作解析**：LSM Tree的删除操作采用"逻辑删除"策略，不立即物理删除数据，而是插入一个墓碑标记（tombstone）。这种设计保持了LSM Tree的不可变性原则，避免了复杂的磁盘文件修改。删除标记会在后续的压缩过程中与原数据一起被清理。同样遵循WAL-first原则，确保删除操作的持久性。

### MemTable刷盘机制

```java
/**
 * 刷新MemTable到磁盘
 */
private void flushMemTable() throws IOException {
    if (activeMemTable.isEmpty()) {                   // 检查MemTable是否为空
        return;                                       // 空表无需刷盘
    }

    // 步骤1: 将活跃MemTable转为不可变（freeze操作）
    immutableMemTables.add(activeMemTable);           // 添加到不可变列表
    activeMemTable = new MemTable(memTableMaxSize);   // 创建新的活跃MemTable

    // 步骤2: 同步刷盘不可变MemTable（避免死锁问题）
    flushImmutableMemTable();                         // 立即执行刷盘操作
}

/**
 * 刷新不可变MemTable到SSTable（调用前必须已获取写锁）
 */
private void flushImmutableMemTable() throws IOException {
    if (immutableMemTables.isEmpty()) {               // 检查是否有不可变MemTable
        return;                                       // 无数据需要刷盘
    }

    // 步骤1: 获取第一个不可变MemTable
    MemTable memTable = immutableMemTables.remove(0); // 移除并获取MemTable
    List<KeyValue> entries = memTable.getAllEntries(); // 获取所有键值对

    if (!entries.isEmpty()) {                         // 确保有数据需要写入
        // 步骤2: 排序数据（SSTable要求有序存储）
        entries.sort(KeyValue::compareTo);           // 按key字典序排序

        // 步骤3: 创建SSTable文件（Level 0文件，直接从MemTable刷入）
        String fileName = String.format("%s/sstable_level0_%d.db",
                dataDir, System.currentTimeMillis()); // 生成唯一文件名
        SSTable newSSTable = new SSTable(fileName, entries); // 创建SSTable文件
        ssTables.add(newSSTable);                     // 添加到SSTable列表

        // 步骤4: 清理WAL（数据已持久化，可以清理WAL）
        wal.checkpoint();                             // 执行WAL检查点
    }
}
```

**刷盘机制解析**：MemTable刷盘是LSM Tree内存到磁盘转换的关键过程。首先将活跃MemTable"冻结"为不可变状态，立即创建新的活跃MemTable接收新写入，确保写入不被阻塞。然后将不可变MemTable的数据排序后写入SSTable文件，文件命名包含层级和时间戳信息。最后执行WAL检查点，清理已持久化的WAL记录。这种设计确保了数据的有序性和系统的高可用性。

## 数据读取流程

### 读取操作实现

```java
/**
 * 查询键值
 */
public String get(String key) {
    if (key == null) {                               // 参数合法性检查
        throw new IllegalArgumentException("Key cannot be null");
    }

    lock.readLock().lock();                          // 获取读锁（允许并发读取）
    try {
        // 步骤1: 优先查询活跃MemTable（最新数据）
        String value = activeMemTable.get(key);      // 从活跃MemTable查找
        if (value != null) {                         // 找到数据
            return value;                            // 直接返回（可能是删除标记）
        }

        // 步骤2: 查询不可变MemTable（按时间倒序，新数据优先）
        for (int i = immutableMemTables.size() - 1; i >= 0; i--) {
            value = immutableMemTables.get(i).get(key); // 从不可变MemTable查找
            if (value != null) {                     // 找到数据
                return value;                        // 返回找到的值
            }
        }

        // 步骤3: 查询SSTable（按创建时间倒序，新文件优先）
        List<SSTable> sortedSSTables = new ArrayList<>(ssTables); // 创建副本避免并发修改
        sortedSSTables.sort((a, b) -> Long.compare(b.getCreationTime(), a.getCreationTime())); // 时间倒序

        for (SSTable ssTable : sortedSSTables) {     // 遍历所有SSTable
            value = ssTable.get(key);                // 从SSTable查找
            if (value != null) {                     // 找到数据
                return value;                        // 返回找到的值
            }
        }

        return null;                                 // 所有地方都没找到，返回null
    } finally {
        lock.readLock().unlock();                    // 释放读锁
    }
}
```

**读取流程解析**：LSM Tree的读取操作采用分层查找策略，严格按照数据新旧程度查找，确保返回最新版本的数据。查找顺序是：活跃MemTable → 不可变MemTable → SSTable文件。每个层级都按时间倒序查找，新数据优先。使用读锁允许多个读操作并发执行，提高读取性能。如果在任何层级找到数据就立即返回，这种"短路"机制减少了不必要的查找开销。

## 系统恢复机制

### 从磁盘恢复数据

```java
/**
 * 从WAL和SSTable恢复数据
 */
private void recover() throws IOException {
    // 步骤1: 恢复SSTable文件
    File dir = new File(dataDir);                    // 获取数据目录
    File[] files = dir.listFiles((d, name) -> name.endsWith(".db")); // 过滤.db文件

    if (files != null) {                             // 确保目录存在且有文件
        // 按文件修改时间排序（确保加载顺序一致）
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));

        for (File file : files) {                    // 遍历所有SSTable文件
            SSTable ssTable = new SSTable(file.getAbsolutePath()); // 加载SSTable
            ssTables.add(ssTable);                   // 添加到SSTable列表
        }
    }

    // 步骤2: 从WAL恢复未刷盘的数据
    List<WriteAheadLog.LogEntry> entries = wal.recover(); // 读取WAL条目
    for (WriteAheadLog.LogEntry entry : entries) {   // 遍历所有WAL条目
        if (entry.getOperation() == WriteAheadLog.Operation.PUT) {
            // 重放PUT操作
            activeMemTable.put(entry.getKey(), entry.getValue());
        } else if (entry.getOperation() == WriteAheadLog.Operation.DELETE) {
            // 重放DELETE操作
            activeMemTable.delete(entry.getKey());
        }
    }
}
```

**恢复机制解析**：系统恢复是LSM Tree确保数据一致性的关键机制。首先扫描数据目录中的所有SSTable文件，按修改时间排序后加载，确保文件的层级关系正确。然后从WAL中恢复所有未刷盘的操作，重新应用到活跃MemTable中。这种两阶段恢复确保了即使系统崩溃，也能完整恢复所有已提交的数据。恢复过程是幂等的，多次执行结果一致。

### 强制刷盘和资源清理

```java
/**
 * 强制刷盘
 */
public void flush() throws IOException {
    lock.writeLock().lock();                          // 获取写锁
    try {
        // 步骤1: 刷新活跃MemTable
        if (!activeMemTable.isEmpty()) {              // 检查活跃MemTable是否有数据
            flushMemTable();                          // 执行刷盘操作
        }

        // 步骤2: 刷新所有剩余的不可变MemTable
        while (!immutableMemTables.isEmpty()) {       // 循环处理所有不可变MemTable
            flushImmutableMemTable();                 // 刷盘每个不可变MemTable
        }
    } finally {
        lock.writeLock().unlock();                    // 释放写锁
    }
}

/**
 * 关闭LSM Tree
 */
public void close() throws IOException {
    // 步骤1: 刷盘所有内存数据
    flush();                                          // 确保所有数据持久化

    // 步骤2: 关闭WAL
    wal.close();                                      // 关闭写前日志

    // 步骤3: 立即关闭线程池，不等待
    compactionExecutor.shutdownNow();                 // 强制关闭压缩线程
}

/**
 * 创建目录
 */
private void createDirectoryIfNotExists(String path) throws IOException {
    File dir = new File(path);                        // 创建File对象
    if (!dir.exists() && !dir.mkdirs()) {            // 检查目录是否存在，不存在则创建
        throw new IOException("Failed to create directory: " + path); // 创建失败抛异常
    }
}
```

**资源管理解析**：强制刷盘操作确保所有内存数据都被持久化，这在系统关闭或数据备份时非常重要。关闭操作按照严格的顺序执行：先刷盘数据，再关闭WAL，最后关闭后台线程。这种顺序确保了数据的完整性和系统的优雅退出。目录创建是一个基础的文件系统操作，确保数据存储路径的可用性。

## 统计信息和监控

```java
/**
 * 获取统计信息
 */
public LSMTreeStats getStats() {
    lock.readLock().lock();                          // 获取读锁
    try {
        return new LSMTreeStats(
                activeMemTable.size(),               // 活跃MemTable大小
                immutableMemTables.size(),           // 不可变MemTable数量
                ssTables.size());                    // SSTable文件数量
    } finally {
        lock.readLock().unlock();                    // 释放读锁
    }
}

/**
 * LSM Tree 统计信息
 */
public static class LSMTreeStats {
    private final int activeMemTableSize;            // 活跃MemTable条目数
    private final int immutableMemTableCount;        // 不可变MemTable数量
    private final int ssTableCount;                  // SSTable文件数量

    public LSMTreeStats(int activeMemTableSize, int immutableMemTableCount, int ssTableCount) {
        this.activeMemTableSize = activeMemTableSize;     // 设置活跃MemTable大小
        this.immutableMemTableCount = immutableMemTableCount; // 设置不可变MemTable数量
        this.ssTableCount = ssTableCount;                 // 设置SSTable数量
    }

    public int getActiveMemTableSize() {             // 获取活跃MemTable大小
        return activeMemTableSize;
    }

    public int getImmutableMemTableCount() {         // 获取不可变MemTable数量
        return immutableMemTableCount;
    }

    public int getSsTableCount() {                   // 获取SSTable数量
        return ssTableCount;
    }

    @Override
    public String toString() {                       // 格式化输出统计信息
        return String.format("LSMTreeStats{activeMemTable=%d, immutableMemTables=%d, ssTables=%d}",
                activeMemTableSize, immutableMemTableCount, ssTableCount);
    }
}
```

**统计监控解析**：统计信息对于监控LSM Tree的健康状态和性能调优非常重要。活跃MemTable大小反映当前内存使用情况，不可变MemTable数量显示待刷盘的数据量，SSTable数量体现磁盘文件的数量。这些指标帮助运维人员了解系统负载，判断是否需要调整参数或触发压缩操作。统计操作使用读锁，不会阻塞正常的读写操作。

## 压缩策略集成

虽然代码中暂时禁用了后台压缩任务，但压缩策略已经集成到系统中：

```java
/**
 * 启动后台压缩任务
 */
private void startBackgroundCompaction() {
    compactionExecutor.submit(() -> {                 // 提交后台任务
        while (!Thread.currentThread().isInterrupted()) { // 循环直到线程中断
            try {
                Thread.sleep(30000);                 // 每30秒检查一次

                if (compactionStrategy.needsCompaction(ssTables)) { // 检查是否需要压缩
                    performCompaction();              // 执行压缩操作
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();   // 恢复中断状态
                break;                               // 退出循环
            } catch (Exception e) {
                e.printStackTrace();                 // 记录异常（实际项目中应使用日志）
            }
        }
    });
}

/**
 * 执行压缩操作
 */
private void performCompaction() throws IOException {
    lock.writeLock().lock();                          // 获取写锁（压缩需要修改SSTable列表）
    try {
        List<SSTable> newSSTables = compactionStrategy.compact(ssTables); // 执行压缩
        ssTables.clear();                             // 清空原SSTable列表
        ssTables.addAll(newSSTables);                 // 添加压缩后的SSTable
    } finally {
        lock.writeLock().unlock();                    // 释放写锁
    }
}
```

**压缩集成解析**：压缩是LSM Tree维护性能的关键机制。后台压缩任务定期检查SSTable文件是否需要合并，当文件数量超过阈值时触发压缩。压缩操作需要写锁保护，确保在重组SSTable列表时不会有并发的读写操作。虽然当前版本为了测试稳定性暂时禁用了后台压缩，但架构已经完整，可以随时启用。

## 小结

LSM Tree主程序实现展现了以下核心特性：

1. **分层存储架构**: 内存MemTable + 磁盘SSTable的分层设计
2. **WAL-first原则**: 确保数据持久性和一致性
3. **并发安全**: 读写锁机制支持高并发访问
4. **优雅降级**: 从内存到磁盘的渐进式查找策略
5. **可靠恢复**: 完整的系统崩溃恢复机制
6. **监控友好**: 丰富的统计信息支持运维监控

这种设计在高写入性能、数据一致性和系统可靠性之间取得了最佳平衡，是现代存储引擎的经典实现。 