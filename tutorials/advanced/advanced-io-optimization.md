# T7: 异步 I/O (Async I/O) 优化技术方案

> **关联任务**：本文档是 [advanced-tasks.md](./advanced-tasks.md) 中 **T7: 异步 I/O 任务** 的详细技术方案。
>
> **前置学习**：建议先完成 [learning-plan.md](./learning-plan.md) 中第 6 天（WAL）和第 9 天（性能测试）的内容。

本文档描述了未来可以实施的高级 I/O 优化任务，旨在提升写入吞吐量和读取性能。

---

## 1. 背景与现状

### 1.1 当前实现

| 组件         | 当前策略   | 实现方式                                   |
| ------------ | ---------- | ------------------------------------------ |
| WAL 写入     | 同步写入   | `FileChannel.write()` + `force(false)`     |
| SSTable 写入 | 同步写入   | `RandomAccessFile` + `FileChannel.write()` |
| SSTable 读取 | 同步读取   | `FileChannel.read()`                       |
| 线程管理     | 简单线程池 | `ExecutorService`                          |

### 1.2 当前选择同步 I/O 的原因

1. **线程安全**：避免 `AsynchronousFileChannel` 创建的系统线程无法正常终止
2. **数据可靠性**：WAL 同步写入保证崩溃恢复无数据丢失
3. **符合业界实践**：RocksDB/LevelDB 的 SSTable 写入也是同步的

### 1.3 性能瓶颈分析

```text
写入路径延迟分解：
┌─────────────────────────────────────────────────────────────┐
│ 用户写入请求                                                  │
├─────────────────────────────────────────────────────────────┤
│ 1. WAL 写入 (同步 fsync)     → 延迟: ~1-10ms (取决于磁盘)    │
│ 2. MemTable 更新 (内存)      → 延迟: ~1μs                   │
│ 3. MemTable 刷盘 (异步)      → 不阻塞用户                    │
│    └─ SSTable 写入 (同步)    → 批量写入，延迟可控             │
└─────────────────────────────────────────────────────────────┘
```

**主要瓶颈**：WAL 的同步 fsync 是写入延迟的主要来源。

---

## 2. 优化方案概览

```text
优先级排序：
┌────────────────────────────────────────────────────────────────┐
│ 优先级 │ 优化方案                    │ 预期收益        │ 复杂度 │
├────────────────────────────────────────────────────────────────┤
│ P0     │ Group Commit (批量提交)     │ 吞吐量 +50-200% │ 中     │
│ P1     │ WAL 批量刷盘策略            │ 延迟 -30%       │ 低     │
│ P2     │ 异步读取 (SSTable)          │ 读取 +100%      │ 高     │
│ P3     │ Direct I/O 支持             │ 延迟 -20%       │ 高     │
│ P4     │ 可配置的持久性级别           │ 灵活性提升       │ 中     │
└────────────────────────────────────────────────────────────────┘
```

---

## 3. P0: Group Commit (批量提交)

### 3.1 原理

将多个并发写入请求合并为一次 WAL 写入和 fsync：

```text
传统方式 (每个请求独立 fsync):
Thread1: write → fsync → return    │ 耗时: 10ms
Thread2: write → fsync → return    │ 耗时: 10ms
Thread3: write → fsync → return    │ 耗时: 10ms
总耗时: 30ms, 吞吐量: 100 ops/sec

Group Commit (合并 fsync):
Thread1: write ─┐
Thread2: write ─┼─→ batch write → fsync → return all
Thread3: write ─┘
总耗时: 10ms, 吞吐量: 300 ops/sec (3x 提升)
```

### 3.2 实现方案

```java
/**
 * Group Commit 实现框架
 */
public class GroupCommitManager {
    private final Object lock = new Object();
    private final List<WriteRequest> pendingWrites = new ArrayList<>();
    private final int maxBatchSize = 1_000_000; // 1MB
    private final long maxWaitMicros = 500;     // 最多等待 500μs

    static class WriteRequest {
        byte[] data;
        CompletableFuture<Long> future;
        long startTime;
    }

    /**
     * 提交写入请求，可能被加入当前批次
     */
    public CompletableFuture<Long> submit(byte[] data) {
        CompletableFuture<Long> future = new CompletableFuture<>();

        synchronized (lock) {
            WriteRequest request = new WriteRequest(data, future, System.nanoTime());
            pendingWrites.add(request);

            // 如果批次满了或已有等待的批次，立即刷盘
            if (shouldFlushNow()) {
                lock.notifyAll(); // 唤醒刷盘线程
            }
        }

        return future;
    }

    /**
     * 批量刷盘线程
     */
    private void flushThread() {
        while (running) {
            List<WriteRequest> batch;

            synchronized (lock) {
                // 等待批次积累或超时
                while (pendingWrites.isEmpty()) {
                    lock.wait(maxWaitMicros / 1000);
                }
                batch = new ArrayList<>(pendingWrites);
                pendingWrites.clear();
            }

            // 执行批量写入 (不需要持有锁)
            try {
                long position = writeBatchToWAL(batch);
                batch.forEach(r -> r.future.complete(position));
            } catch (IOException e) {
                batch.forEach(r -> r.future.completeExceptionally(e));
            }
        }
    }
}
```

### 3.3 配置参数

| 参数            | 默认值 | 说明         |
| --------------- | ------ | ------------ |
| `maxBatchSize`  | 1MB    | 最大批次大小 |
| `maxWaitMicros` | 500μs  | 最长等待时间 |
| `maxBatchCount` | 100    | 最大请求数量 |

### 3.4 预期收益

- **吞吐量提升**：50-200%（取决于并发度）
- **延迟降低**：高并发场景下延迟更稳定
- **IOPS 减少**：减少 fsync 调用次数

---

## 4. P1: WAL 批量刷盘策略

### 4.1 原理

当前实现每次写入都调用 `force(false)`，可以改为按时间或数量批量刷盘：

```java
public class WriteAheadLog {
    private int pendingSyncCount = 0;
    private long lastSyncTime = 0;

    // 配置参数
    private int syncBatchSize = 100;      // 每 100 条刷盘一次
    private long syncIntervalMillis = 10; // 或每 10ms 刷盘一次

    public void append(LogEntry entry) throws IOException {
        synchronized (lock) {
            channel.write(buffer, position);
            pendingSyncCount++;

            // 判断是否需要刷盘
            if (shouldSync()) {
                channel.force(false);
                pendingSyncCount = 0;
                lastSyncTime = System.currentTimeMillis();
            }
        }
    }

    private boolean shouldSync() {
        return pendingSyncCount >= syncBatchSize ||
               System.currentTimeMillis() - lastSyncTime >= syncIntervalMillis;
    }
}
```

### 4.2 持久性级别

| 级别       | 配置           | 持久性保证 | 性能 |
| ---------- | -------------- | ---------- | ---- |
| `SYNC`     | 每次写入 fsync | 最高       | 最慢 |
| `BATCH`    | 批量 fsync     | 中等       | 中等 |
| `PERIODIC` | 定时 fsync     | 较低       | 快   |
| `ASYNC`    | 由 OS 决定     | 最低       | 最快 |

### 4.3 API 设计

```java
// 创建 LSM Tree 时配置 WAL 策略
LSMTree tree = new LSMTree.Builder(dataDir)
    .walSyncPolicy(SyncPolicy.BATCH)  // SYNC, BATCH, PERIODIC, ASYNC
    .walSyncBatchSize(100)
    .walSyncIntervalMillis(10)
    .build();
```

---

## 5. P2: 异步读取优化

### 5.1 原理

RocksDB 的实践证明，异步 I/O 对 **读取** 场景收益巨大：

- MultiGet: 吞吐量提升 **2.5x**
- Scan: 延迟降低 **50%**

### 5.2 适用场景

```text
异步读取收益场景：
┌────────────────────────────────────────────────────────┐
│ 场景              │ 异步收益  │ 说明                    │
├────────────────────────────────────────────────────────┤
│ MultiGet (批量查询) │ 高        │ 可并行读取多个 SSTable   │
│ Range Query (范围) │ 中高      │ 预取下一数据块           │
│ Single Get (单点)  │ 低        │ 无并行机会               │
└────────────────────────────────────────────────────────┘
```

### 5.3 实现方案

```java
/**
 * 异步 SSTable 读取器
 */
public class AsyncSSTableReader {
    private final ExecutorService readExecutor;
    private final AsynchronousFileChannel channel;

    /**
     * 批量异步读取
     */
    public CompletableFuture<List<KeyValue>> multiGetAsync(List<String> keys) {
        // 1. 分析每个 key 可能在的 SSTable
        Map<SSTable, List<String>> keyDistribution = analyzeKeyDistribution(keys);

        // 2. 并行发起异步读取
        List<CompletableFuture<List<KeyValue>>> futures = new ArrayList<>();
        for (Map.Entry<SSTable, List<String>> entry : keyDistribution.entrySet()) {
            futures.add(readFromSSTableAsync(entry.getKey(), entry.getValue()));
        }

        // 3. 合并结果
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .flatMap(f -> f.join().stream())
                .collect(Collectors.toList()));
    }

    /**
     * 范围查询预取
     */
    public void prefetchNextBlock(String currentKey) {
        // 在后台预取下一个可能需要的数据块
        readExecutor.submit(() -> {
            // 预取逻辑
        });
    }
}
```

### 5.4 关键实现细节

1. **线程池管理**：使用独立的有界线程池，避免线程泄漏
2. **资源清理**：确保 `AsynchronousFileChannel` 正确关闭
3. **错误处理**：异步操作的异常需要正确传播

---

## 6. P3: Direct I/O 支持

### 6.1 原理

Direct I/O 绕过 OS 页缓存，减少内存拷贝：

```text
传统 I/O:
磁盘 → OS 页缓存 → JVM 堆内存 → 用户缓冲区
       (拷贝1)      (拷贝2)

Direct I/O:
磁盘 → DirectByteBuffer
       (零拷贝)
```

### 6.2 适用场景

- **大文件顺序读写**：SSTable 刷盘、Compaction
- **SSD 存储**：减少 CPU 开销
- **内存受限场景**：避免双重缓存

### 6.3 实现方案

```java
/**
 * Direct I/O 写入
 */
public class DirectIOWriter {
    public void writeWithDirectIO(Path file, byte[] data) throws IOException {
        // 使用 DirectByteBuffer
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(data.length);
        directBuffer.put(data);
        directBuffer.flip();

        // 打开文件时指定 Direct I/O (Linux)
        Set<StandardOpenOption> options = new HashSet<>();
        options.add(StandardOpenOption.WRITE);
        options.add(StandardOpenOption.CREATE);
        // Linux: 需要通过扩展属性或 native 调用设置 O_DIRECT

        try (FileChannel channel = FileChannel.open(file, options)) {
            channel.write(directBuffer);
        }
    }
}
```

### 6.4 注意事项

- 需要对齐写入（块大小对齐，通常 4KB）
- 不适合小文件随机读写
- 需要 JVM 配置支持大页内存

---

## 7. P4: 可配置的持久性级别

### 7.1 设计目标

参考 PostgreSQL 的 `synchronous_commit` 参数，提供灵活的持久性配置：

```java
public enum DurabilityLevel {
    /**
     * 最高持久性：每次写入都 fsync
     */
    SYNC(true, true),

    /**
     * 批量持久性：按批次 fsync
     */
    BATCH_SYNC(true, false),

    /**
     * 异步持久性：依赖 OS 刷盘
     */
    ASYNC(false, false);

    public final boolean syncOnWrite;
    public final boolean syncImmediately;
}
```

### 7.2 使用示例

```java
// 金融场景：最高持久性
LSMTree bankDb = new LSMTree.Builder("/data/bank")
    .durability(DurabilityLevel.SYNC)
    .build();

// 日志场景：高性能
LSMTree logDb = new LSMTree.Builder("/data/logs")
    .durability(DurabilityLevel.ASYNC)
    .build();

// 通用场景：平衡
LSMTree appDb = new LSMTree.Builder("/data/app")
    .durability(DurabilityLevel.BATCH_SYNC)
    .walSyncBatchSize(100)
    .build();
```

---

## 8. P5: 实施路线图

### 8.1 Phase 1: 基础优化 (2-3 周)

- [ ] 实现 WAL 批量刷盘策略
- [ ] 添加持久性级别配置 API
- [ ] 编写性能基准测试

### 8.2 Phase 2: Group Commit (3-4 周)

- [ ] 设计 Group Commit 架构
- [ ] 实现批量写入合并
- [ ] 并发测试和调优

### 8.3 Phase 3: 异步读取 (4-5 周)

- [ ] 实现异步 MultiGet
- [ ] 实现范围查询预取
- [ ] 线程池管理和资源清理
- [ ] 性能对比测试

### 8.4 Phase 4: Direct I/O (可选) (2-3 周)

- [ ] 实现 Direct I/O 写入
- [ ] 对齐处理
- [ ] 性能对比测试

---

## 9. P6: 性能验证方案

### 9.1 基准测试配置

```bash
# 写入性能测试
java -jar java-lsm-tree-1.0.0.jar \
    --benchmark write \
    --threads 16 \
    --records 1000000 \
    --value-size 1024 \
    --wal-policy batch \
    --batch-size 100

# 读取性能测试
java -jar java-lsm-tree-1.0.0.jar \
    --benchmark read \
    --threads 16 \
    --records 1000000 \
    --async-read true
```

### 9.2 预期性能指标

| 指标                | 当前值        | 优化后目标     | 提升  |
| ------------------- | ------------- | -------------- | ----- |
| 写入吞吐量 (单线程) | 5,000 ops/s   | 8,000 ops/s    | +60%  |
| 写入吞吐量 (16线程) | 20,000 ops/s  | 50,000 ops/s   | +150% |
| MultiGet 延迟 (P99) | 10ms          | 4ms            | -60%  |
| 范围查询吞吐量      | 50,000 rows/s | 100,000 rows/s | +100% |

---

## 10. P7: 参考资料

1. [RocksDB WAL Performance](https://github.com/facebook/rocksdb/wiki/WAL-Performance)
2. [Asynchronous IO in RocksDB](https://rocksdb.org/blog/2022/10/07/asynchronous-io-in-rocksdb.html)
3. [RocksDB FlushWAL](https://rocksdb.org/blog/2017/08/25/flushwal.html)
4. [PostgreSQL synchronous_commit](https://www.postgresql.org/docs/current/wal-async-commit.html)

---
