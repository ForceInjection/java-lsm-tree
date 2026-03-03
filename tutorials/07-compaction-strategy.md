# 第7章：压缩策略

## 1. 什么是压缩？

**压缩 (Compaction)** 是 LSM Tree 保持长期健康运行的生命线。它本质上是一个后台维护过程，负责将多个 SSTable 文件合并、整理、重写。

如果没有压缩，LSM Tree 会面临以下严重问题：

- **读性能劣化**: 随着写入增加，Level 0 的 SSTable 文件会无限增多。读取一个 Key 可能需要扫描成千上万个文件。
- **空间膨胀**: 同一个 Key 的多次更新会产生多个版本的数据，删除的数据也只是标记为 Tombstone。这些无效数据会占用大量磁盘空间。

因此，压缩的主要目标是：

1. **控制文件数量**: 将大量小文件合并为少量大文件，保持层级结构扁平。
2. **回收空间**: 物理删除被覆盖的旧数据和已过期的 Tombstone。
3. **提升局部性**: 将键范围重叠的 SSTable 合并为不重叠的有序文件。

---

## 2. 压缩触发条件

压缩通常是自动触发的，常见的触发因子包括：

```text
1. 文件数量阈值: 某层 (Level N) 的文件数超过限制 (如 Level 0 > 4 个)。
2. 文件大小阈值: 某层的文件总大小超过限制 (如 Level 1 > 1GB)。
3. 读放大监测: 系统检测到某个 Key 的查询穿透了太多文件。
4. 手动触发: 运维人员执行 `compact` 命令（通常在夜间低峰期）。
```

---

## 3. 压缩策略类型

业界主要有两种主流的压缩策略：**Size-Tiered** (Cassandra, HBase) 和 **Leveled** (LevelDB, RocksDB)。

### 3.1 Size-Tiered 压缩策略 (STCS)

本教程采用 **Size-Tiered 压缩**，因为它的实现逻辑相对简单且写入性能极佳。

**核心思想**：

- 将大小相近的 SSTable 分为一组（Tier/Level）。
- 当某一层积攒了足够多（如 4 个）的 SSTable 时，将它们一次性合并，生成一个更大的 SSTable，并放入下一层。

**工作流程演示**：

| 层级        | 状态                          | 触发行为                                                     |
| :---------- | :---------------------------- | :----------------------------------------------------------- |
| **Level 0** | `[10MB] [10MB] [10MB] [10MB]` | 积攒了 4 个文件 -> 触发合并 -> 生成 1 个 40MB 文件推送到 L1  |
| **Level 1** | `[40MB]`                      | 只有 1 个文件，等待更多...                                   |
| ...         | ...                           | ...                                                          |
| **Level 1** | `[40MB] [40MB] [40MB] [40MB]` | 积攒了 4 个文件 -> 触发合并 -> 生成 1 个 160MB 文件推送到 L2 |

**优势与劣势**：

- ✅ **写放大低**: 每个数据只需合并少数几次即可到达底层。
- ⚠️ **读放大高**: 每一层的文件之间 Key 可能完全重叠，读取时可能需要查询每一层的所有文件。
- ⚠️ **空间放大高**: 在合并大层级（如 L2 -> L3）时，需要同时保留输入文件和输出文件，可能导致磁盘空间使用率瞬间翻倍。

### 3.2 Leveled 压缩策略 (LCS) - 补充知识

**LevelDB/RocksDB** 默认采用此策略。

- **核心**: 每一层（Level 1+）的文件之间 Key **互不重叠**。
- **优点**: 读取性能极其稳定（除 L0 外，每层只需查 1 个文件）。
- **缺点**: 写放大较高，一个文件可能会被反复合并多次。

---

## 4. 压缩策略实现

### 4.1 策略管理器

```java
package com.brianxiadong.lsmtree;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CompactionStrategy {
    private final String dataDirectory;
    private final int maxFilesPerLevel;              // 触发阈值 (如 4)
    private final Map<Integer, List<String>> levelFiles; // 内存中维护的文件层级视图

    public CompactionStrategy(String dataDirectory, int maxFilesPerLevel) {
        this.dataDirectory = dataDirectory;
        this.maxFilesPerLevel = maxFilesPerLevel;
        this.levelFiles = new ConcurrentHashMap<>();
    }

    // 注册新生成的 SSTable (通常是 Level 0)
    public void addSSTable(String filePath) {
        levelFiles.computeIfAbsent(0, k -> new ArrayList<>()).add(filePath);
    }

    // 检查是否需要压缩
    public boolean needsCompaction(int level) {
        List<String> files = levelFiles.get(level);
        return files != null && files.size() >= maxFilesPerLevel;
    }

    // 递归执行压缩
    public void compact(int level) throws IOException {
        if (!needsCompaction(level)) {
            return;
        }

        // 1. 选定输入文件
        List<String> filesToCompact = new ArrayList<>(levelFiles.get(level));

        // 2. 执行合并 (最耗时的步骤)
        String compactedFile = performCompaction(filesToCompact, level + 1);

        // 3. 更新元数据 (原子性更新是难点，这里简化处理)
        levelFiles.get(level).clear();
        levelFiles.computeIfAbsent(level + 1, k -> new ArrayList<>()).add(compactedFile);

        // 4. 清理旧文件
        cleanupOldFiles(filesToCompact);

        // 5. 级联检查: L1 的增加可能触发 L1 -> L2 的压缩
        if (needsCompaction(level + 1)) {
            compact(level + 1);
        }
    }
}
```

### 4.2 核心算法：多路归并排序

压缩的核心就是**多路归并排序 (K-way Merge Sort)**。因为输入的 SSTable 本身就是有序的，我们不需要把所有数据读到内存重排，而是使用流式归并。

```java
private List<KeyValue> mergeSSTableData(List<SSTable> tables) {
    // 最小堆：始终持有每个 SSTable 当前未处理的最小 Key
    PriorityQueue<SSTableIterator> heap = new PriorityQueue<>(
        Comparator.comparing(iter -> iter.current().getKey())
    );

    // 初始化：将每个表的第一个元素入堆
    for (SSTable table : tables) {
        SSTableIterator iter = table.iterator();
        if (iter.hasNext()) {
            iter.next();
            heap.offer(iter);
        }
    }

    List<KeyValue> merged = new ArrayList<>();

    // 循环弹出堆顶最小元素
    while (!heap.isEmpty()) {
        SSTableIterator iter = heap.poll();
        KeyValue current = iter.current();
        merged.add(current);

        // 如果该表还有数据，补充入堆
        if (iter.hasNext()) {
            iter.next();
            heap.offer(iter);
        }
    }

    return merged;
}
```

**复杂度分析**:

- 时间复杂度: **O(N log K)**，其中 N 是总记录数，K 是参与合并的文件数。
- 空间复杂度: **O(K)**，仅需维持 K 个迭代器和堆，内存占用极低，可处理 TB 级数据。

### 4.3 数据清洗逻辑

在归并过程中，我们有机会"清洗"数据。

```java
private List<KeyValue> deduplicateAndClean(List<KeyValue> sortedData) {
    List<KeyValue> cleaned = new ArrayList<>();
    String lastKey = null;
    KeyValue lastKV = null;

    for (KeyValue kv : sortedData) {
        // 如果遇到了新 Key，说明上一个 Key 的版本收集完毕
        if (!kv.getKey().equals(lastKey)) {
            // 将上一个 Key 的最终版本写入结果集
            if (lastKV != null && !lastKV.isDeleted()) {
                cleaned.add(lastKV);
            }
            lastKey = kv.getKey();
            lastKV = kv;
        } else {
            // 如果 Key 相同，保留时间戳更大的那个 (LWW)
            if (kv.getTimestamp() > lastKV.getTimestamp()) {
                lastKV = kv;
            }
            // 较旧的版本在这里被静默丢弃了 (Space Reclaimed!)
        }
    }

    // 处理最后一个 Key
    if (lastKV != null && !lastKV.isDeleted()) {
        cleaned.add(lastKV);
    }

    return cleaned;
}
```

**墓碑的处理**:
注意代码中的 `!lastKV.isDeleted()` 判断。如果最终版本是墓碑，我们选择**不写入**结果集，这就实现了物理删除。

> **注意**: 在分布式系统中，过早删除墓碑可能导致"数据复活"问题（如果旧数据在其他节点还存在）。通常需要等待一个 `gc_grace_seconds` 周期后才能真正丢弃墓碑。

---

## 5. 小结

压缩策略是 LSM Tree 的"心脏"，它不断跳动以维持系统的活力。

1. **Size-Tiered**: 适合写密集型，结构简单，但读放大较大。
2. **归并排序**: 高效的流式处理算法，内存占用低。
3. **空间回收**: 在合并过程中悄无声息地完成了垃圾回收和版本去重。

---

## 6. 思考题

1. **写停顿 (Write Stall)**: 如果压缩速度赶不上写入速度，磁盘会被撑满吗？LSM Tree 通常会如何自我保护？
2. **通用性**: 为什么 Cassandra 选择 Size-Tiered，而 RocksDB 默认选择 Leveled？这反映了它们对读/写性能的何种取舍？
3. **并发压缩**: 如何设计多线程压缩？如果同时有 L0->L1 和 L1->L2 的压缩任务，如何避免文件冲突？

**下一章预告**: 我们将把 MemTable, SSTable, WAL, Compaction 全部组装起来，构建一个完整的 LSM Tree 存储引擎。
