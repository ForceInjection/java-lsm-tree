# 第8章：LSM Tree 主程序实现

## 1. 核心架构设计

`LSMTree` 类是整个存储引擎的门面 (Facade) 和控制中心。它不仅要对外提供简单的 `put/get/delete` 接口，还要在内部协调 MemTable、SSTable、WAL 和压缩线程的复杂交互。

### 1.1 主要组件结构

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
 * 线程安全，支持并发读写
 */
public class LSMTree implements AutoCloseable {
    private final String dataDir;
    private final int memTableMaxSize;

    // 全局读写锁：
    // - 写锁：用于 put/delete/flush 等修改内存结构的操作 (互斥)
    // - 读锁：用于 get/stats 等查询操作 (共享)
    private final ReadWriteLock lock;

    // 内存组件
    private volatile MemTable activeMemTable;           // 当前接收写入的 MemTable
    private final List<MemTable> immutableMemTables;    // 等待刷盘的 MemTable 队列

    // 磁盘组件
    private final List<SSTable> ssTables;               // 已持久化的 SSTable 列表

    // 后台任务
    private final ExecutorService compactionExecutor;   // 单线程执行压缩，避免并发冲突
    private final CompactionStrategy compactionStrategy;

    // 持久化组件
    private final WriteAheadLog wal;

    // LSM Tree 构造器
    public LSMTree(String dataDir, int memTableMaxSize) throws IOException {
        this.dataDir = dataDir;
        this.memTableMaxSize = memTableMaxSize;
        this.lock = new ReentrantReadWriteLock();

        createDirectoryIfNotExists(dataDir);

        // 初始化组件
        this.activeMemTable = new MemTable(memTableMaxSize);
        this.immutableMemTables = new ArrayList<>();
        this.ssTables = new ArrayList<>();

        // 策略：L0 达到 4 个文件触发压缩，每层容量放大 10 倍
        this.compactionStrategy = new CompactionStrategy(dataDir, 4, 10);

        this.wal = new WriteAheadLog(dataDir + "/wal.log");

        // 启动后台压缩线程 (Daemon 线程，随 JVM 退出而退出)
        this.compactionExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LSMTree-Compaction");
            t.setDaemon(true);
            return t;
        });

        // 关键步骤：启动时恢复数据 (Crash Recovery)
        recover();
    }
}
```

**架构设计解析**：

- **读写分离锁**: 使用 `ReentrantReadWriteLock` 是实现高并发读的关键。只有写操作和结构变更（如 Flush/Compaction）需要独占锁，绝大多数读操作可以并行执行。
- **双层内存表**: `active` 和 `immutable` 的设计使得刷盘操作可以异步进行，不会长时间阻塞写入请求（只有在切换瞬间需要短暂加锁）。

---

## 2. 数据写入流程

### 2.1 写入操作实现

```java
/**
 * 插入键值对
 */
public void put(String key, String value) throws IOException {
    if (key == null || value == null) {
        throw new IllegalArgumentException("Key and value cannot be null");
    }

    lock.writeLock().lock();                          // 获取写锁
    try {
        // 1. WAL: 先写日志，确保持久性 (Crash Safe)
        wal.append(WriteAheadLog.LogEntry.put(key, value));

        // 2. MemTable: 更新内存，速度极快
        activeMemTable.put(key, value);

        // 3. Check: 检查是否需要触发刷盘
        if (activeMemTable.shouldFlush()) {
            flushMemTable();                          // 触发刷盘 (同步或异步)
        }
    } finally {
        lock.writeLock().unlock();
    }
}
```

### 2.2 删除操作实现

```java
public void delete(String key) throws IOException {
    // 参数检查略...
    lock.writeLock().lock();
    try {
        // 1. WAL: 记录删除操作
        wal.append(WriteAheadLog.LogEntry.delete(key));

        // 2. MemTable: 写入 Tombstone (逻辑删除)
        activeMemTable.delete(key);

        // 3. Check: 即使是删除，也会占用内存空间，也可能触发刷盘
        if (activeMemTable.shouldFlush()) {
            flushMemTable();
        }
    } finally {
        lock.writeLock().unlock();
    }
}
```

### 2.3 MemTable 刷盘机制

刷盘是将内存数据转换为磁盘文件的关键步骤。

```java
/**
 * 内存表刷盘流程
 */
private void flushMemTable() throws IOException {
    // 1. 快速切换 (持有写锁期间)
    if (activeMemTable.isEmpty()) return;

    // 将 Active 转为 Immutable
    immutableMemTables.add(activeMemTable);
    // 创建新的 Active MemTable 接收后续写入
    activeMemTable = new MemTable(memTableMaxSize);

    // 2. 执行物理刷盘 (持有写锁期间 - 简化版)
    // 在生产级实现中，这一步通常会释放写锁，由后台线程异步执行，
    // 以避免阻塞主写入线程。但这里为了保证数据一致性简单，采用了同步刷盘。
    flushImmutableMemTable();
}

private void flushImmutableMemTable() throws IOException {
    if (immutableMemTables.isEmpty()) return;

    MemTable memTable = immutableMemTables.remove(0);
    List<KeyValue> entries = memTable.getAllEntries();

    if (!entries.isEmpty()) {
        // 排序 (SSTable 要求)
        entries.sort(KeyValue::compareTo);

        // 写入磁盘
        String fileName = String.format("%s/sstable_level0_%d.db",
                dataDir, System.currentTimeMillis());
        SSTable newSSTable = new SSTable(fileName, entries);
        ssTables.add(newSSTable);

        // 关键：刷盘成功后，WAL 就可以安全截断了
        wal.checkpoint();
    }
}
```

---

## 3. 数据读取流程

读取遵循**"从新到旧"**的查找链，一旦找到立即返回（Short-circuit）。

### 3.1 读取操作实现

```java
public String get(String key) {
    lock.readLock().lock();                          // 获取读锁 (允许并发)
    try {
        // 1. 查 Active MemTable (最新)
        String value = activeMemTable.get(key);
        if (value != null) return value;

        // 2. 查 Immutable MemTables (次新，倒序遍历)
        for (int i = immutableMemTables.size() - 1; i >= 0; i--) {
            value = immutableMemTables.get(i).get(key);
            if (value != null) return value;
        }

        // 3. 查 SSTables (磁盘，倒序遍历)
        // 注意：这里需要创建一个视图副本或确保遍历安全
        List<SSTable> searchList = new ArrayList<>(ssTables);
        // 按时间倒序排序，确保先查到较新的文件
        searchList.sort((a, b) -> Long.compare(b.getCreationTime(), a.getCreationTime()));

        for (SSTable ssTable : searchList) {
            // 这里会先经过 BloomFilter 过滤
            value = ssTable.get(key);
            if (value != null) return value;
        }

        return null;                                 // 全都没找到
    } finally {
        lock.readLock().unlock();
    }
}
```

---

## 4. 系统恢复机制

当系统重启时，需要恢复内存状态。

```java
private void recover() throws IOException {
    // 1. 加载 SSTable 文件 (构建磁盘索引)
    File dir = new File(dataDir);
    File[] files = dir.listFiles((d, name) -> name.endsWith(".db"));

    if (files != null) {
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File file : files) {
            ssTables.add(new SSTable(file.getAbsolutePath()));
        }
    }

    // 2. 重放 WAL (恢复 MemTable)
    // 只有那些写入了 WAL 但没来得及刷成 SSTable 的数据需要恢复
    List<WriteAheadLog.LogEntry> entries = wal.recover();
    for (WriteAheadLog.LogEntry entry : entries) {
        if (entry.getOperation() == WriteAheadLog.Operation.PUT) {
            activeMemTable.put(entry.getKey(), entry.getValue());
        } else if (entry.getOperation() == WriteAheadLog.Operation.DELETE) {
            activeMemTable.delete(entry.getKey());
        }
    }
}
```

**恢复的幂等性**:
恢复过程是幂等的。即使多次重放 WAL，MemTable 的最终状态也是一致的（基于 LWW 原则）。

---

## 5. 资源管理与关闭

```java
public void close() throws IOException {
    // 1. 强制刷盘: 确保所有内存数据持久化
    flush();

    // 2. 关闭 WAL
    wal.close();

    // 3. 关闭后台线程
    compactionExecutor.shutdownNow();
}
```

---

## 6. 统计信息和监控

对于存储引擎，**可观测性 (Observability)** 至关重要。

```java
public LSMTreeStats getStats() {
    lock.readLock().lock();
    try {
        return new LSMTreeStats(
                activeMemTable.size(),
                immutableMemTables.size(),
                ssTables.size());
    } finally {
        lock.readLock().unlock();
    }
}
```

**关键指标**:

- **MemTable 大小**: 监控是否内存泄漏或写入过快。
- **Immutable 数量**: 如果堆积过多，说明刷盘速度跟不上写入速度 (Write Stall 风险)。
- **SSTable 数量**: 如果过多，说明 Compaction 滞后，读取性能将下降。

---

## 7. 小结

通过本章的组装，我们得到了一个功能完整的 LSM Tree 存储引擎。

1. **并发控制**: 读写锁 + volatile + 线程封闭。
2. **分层架构**: 内存与磁盘的完美配合。
3. **可靠性**: WAL + Crash Recovery 保证数据不丢。
4. **可维护性**: 模块化设计，各组件职责单一。

这个实现虽然简化了许多细节（如 Block Cache, 异步 IO, 精细的锁粒度），但它完整地展示了现代 KV 存储引擎的骨架。
