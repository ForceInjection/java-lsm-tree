# Java LSM Tree 生产就绪性分析：从教学版到生产级

> **关联任务**：本文档是 [advanced-tasks.md](./advanced-tasks.md) 中 **T1 & T3** 的核心参考文档。
>
> **前置学习**：建议先完成 [learning-plan.md](./learning-plan.md) 中第 7 天（T3）和第 11-12 天（T1）的内容。

本文档深入分析了当前代码实现中的关键"简化点"，解释了其设计初衷，并提供了生产环境下的改进方案。这对于理解 LSM Tree 的性能瓶颈和优化方向至关重要。

## 1. 引言

本项目作为一个教学性质的 LSM Tree 实现，为了保持代码的简洁性和可读性，在某些核心组件的设计上做出了权衡（Trade-offs）。特别是在 **SSTable 查询**和**数据压缩（Compaction）**两个方面，采用了最直观但非性能最优的实现方式。

本文将重点分析 `SSTable.java` 和 `LeveledCompactionStrategy.java` 中的简化实现，并对比生产级系统（如 LevelDB, RocksDB）的解决方案。

---

## 2. SSTable 查询机制：全表扫描 vs 稀疏索引

### 2.1 当前实现 (SSTable.java)

当前 `SSTable.java` 的 `get(String key)` 方法采用的是**线性扫描**策略，虽然结合了布隆过滤器（Bloom Filter）进行快速否定，但一旦布隆过滤器判断"可能存在"，就需要读取文件并从头开始扫描。

**代码片段分析** (`SSTable.java`):

```java
public String get(String key) {
    // 1. 布隆过滤器快速检查（高效）
    if (!bloomFilter.mightContain(key)) {
        return null;
    }

    // 2. 打开文件流（IO 开销）
    try (DataInputStream dis = openPayloadInput()) {
        int totalEntries = dis.readInt();
        // 3. 线性扫描所有条目（性能瓶颈：O(N)）
        for (int i = 0; i < totalEntries; i++) {
            String currentKey = dis.readUTF();
            // ... 读取 value ...
            if (currentKey.equals(key)) {
                return deleted ? null : value;
            }
            // ...
        }
    }
    // ...
}
```

**简化原因**：

- **格式简单**：文件格式仅为简单的 Key-Value 顺序列表，无需维护复杂的 Block 结构和索引元数据。
- **代码直观**：无需处理 Block 的切分、索引的构建和二分查找逻辑。

**性能问题**：

- **IO 放大**：即使只查询一个 Key，也可能需要读取大量无关数据（直到找到 Key 或文件结束）。
- **CPU 开销**：大量的字符串反序列化和比较操作。
- **延迟不可控**：查询延迟随文件大小线性增长。

### 2.2 生产级改进方案：稀疏索引与 Block 结构

生产级 SSTable 通常采用 **Block-based** 格式配合 **稀疏索引 (Sparse Index)**。

#### 2.2.1 改进设计

1. **数据分块 (Data Blocks)**：将有序的 Key-Value 对按固定大小（如 4KB）切分为多个 Block。
2. **索引块 (Index Block)**：记录每个 Data Block 的**最后一个 Key**（或第一个 Key）以及该 Block 在文件中的**偏移量 (Offset)**。
3. **尾部元数据 (Footer)**：记录 Index Block 的位置、布隆过滤器数据等。

#### 2.2.2 改进后的查询流程 (O(log N))

1. **读取 Footer**：获取 Index Block 的位置。
2. **加载 Index**：将 Index Block 加载到内存（通常常驻内存）。
3. **二分查找 Index**：在 Index 中二分查找目标 Key，定位到它可能存在的 **Data Block**。
4. **加载 Block**：仅从磁盘读取该 Data Block（例如 4KB），而不是整个文件。
5. **块内查找**：在内存中的 Data Block 内进行二分查找或线性扫描。

#### 2.2.3 伪代码示例

```java
public String getOptimized(String key) {
    // 1. 内存中的稀疏索引二分查找
    BlockIndexEntry entry = index.binarySearch(key);
    if (entry == null) return null;

    // 2. 仅读取目标 Block
    Block block = readBlock(entry.offset, entry.size);

    // 3. 块内查找
    return block.search(key);
}
```

---

## 3. 压缩策略：全量内存合并 vs 流式归并

### 3.1 当前实现 (LeveledCompactionStrategy.java)

当前 `compactLevel` 方法采用了**全量加载**的方式，将所有待合并的 SSTable 数据一次性读入内存，排序后再写出。

**代码片段分析** (`LeveledCompactionStrategy.java`):

```java
private List<SSTable> compactLevel(List<SSTable> tables, int targetLevel) {
    // 1. 内存风险点：加载所有数据到 List
    List<KeyValue> allEntries = new ArrayList<>();
    for (SSTable table : tables) {
        allEntries.addAll(table.getAllEntries());
    }

    // 2. 内存排序和去重
    List<KeyValue> mergedEntries = mergeAndDedup(allEntries);

    // 3. 写出新文件
    // ...
}
```

**简化原因**：

- **利用 Java 集合框架**：直接使用 `ArrayList` 和 `Collections.sort` (或 TreeMap) 实现逻辑非常简单。
- **避免复杂的迭代器管理**：不需要手动管理多个文件的读取游标。

**严重问题**：

- **OOM 风险 (Out Of Memory)**：内存占用与**数据总量**成正比。如果 Level 1 有 1GB 数据，合并时就需要 1GB+ 的堆内存，这在生产环境中是不可接受的。
- **GC 压力**：创建海量的临时对象（KeyValue），导致频繁的 Full GC。

### 3.2 生产级改进方案：流式归并排序 (Streaming Merge Sort)

生产环境必须保证内存占用是**常数级 (O(1))** 或仅与**并发流数量**相关，而与数据总量无关。这通过**多路归并排序 (K-way Merge Sort)** 实现。

#### 3.2.1 改进设计

1. **迭代器模式**：每个 SSTable 提供一个 `PeekingIterator`，支持按需读取下一个 Key-Value，而不是一次性加载所有。
2. **最小堆 (PriorityQueue)**：维护一个大小为 N (N=待合并文件数) 的最小堆。
3. **流式写入**：
   - 将所有 SSTable 的 Iterator 放入最小堆（按 Key 排序）。
   - 不断从堆顶取出最小的 Key-Value。
   - 处理去重逻辑（如果 Key 与上一个相同，取最新的）。
   - 将结果直接写入新的 SSTable 输出流。
   - 如果 Iterator 还有数据，读取下一条并放回堆中。

#### 3.2.2 伪代码示例

```java
public void compactLevelStreamed(List<SSTable> tables, Writer writer) {
    // 1. 创建迭代器堆
    PriorityQueue<PeekingIterator<KeyValue>> heap = new PriorityQueue<>(
        (a, b) -> a.peek().key.compareTo(b.peek().key)
    );

    // 2. 初始化堆
    for (SSTable table : tables) {
        heap.add(table.iterator());
    }

    // 3. 流式归并
    KeyValue lastKv = null;
    while (!heap.isEmpty()) {
        PeekingIterator<KeyValue> currentIter = heap.poll();
        KeyValue currentKv = currentIter.next();

        // 将迭代器放回堆
        if (currentIter.hasNext()) {
            heap.add(currentIter);
        }

        // 4. 去重与合并逻辑
        if (lastKv == null) {
            lastKv = currentKv;
        } else if (currentKv.key.equals(lastKv.key)) {
            // 保留版本更新的（假设时间戳更大或序列号更大）
            if (currentKv.timestamp > lastKv.timestamp) {
                lastKv = currentKv;
            }
        } else {
            // Key 变了，说明 lastKv 是该 Key 的最终版本，写入磁盘
            writer.append(lastKv);
            lastKv = currentKv;
        }
    }

    if (lastKv != null) {
        writer.append(lastKv);
    }
}
```

**改进收益**：

- **内存占用极低**：内存中仅需维持 N 个 KeyValue 对象（N 为文件数），无论合并 1GB 还是 1TB 数据，内存占用都基本不变。
- **高吞吐**：数据像流水线一样流过 CPU，缓存友好。

---

## 4. 总结

| 特性                  | 当前教学版实现 | 生产级改进方案                        | 核心差异                          |
| :-------------------- | :------------- | :------------------------------------ | :-------------------------------- |
| **SSTable 查询**      | 全文件线性扫描 | **稀疏索引 + Block 二分查找**         | O(N) vs O(log N)，IO 效率天壤之别 |
| **SSTable 格式**      | 纯 KV 列表     | **Data Block + Index Block + Footer** | 支持随机访问                      |
| **合并 (Compaction)** | 全量内存加载   | **多路流式归并 (K-way Merge)**        | 内存占用 O(DataSize) vs O(1)      |
| **WAL**               | 文本格式       | **二进制/Protobuf + CRC 校验**        | 解析速度与数据完整性              |

在完成 `advanced-tasks.md` 中的 **T1 (Range Query)** 和 **T3 (Advanced Compaction)** 时，强烈建议尝试实现上述的生产级方案，这将显著提升系统的性能上限和稳定性。
