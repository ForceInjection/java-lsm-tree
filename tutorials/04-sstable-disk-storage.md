# 第4章：SSTable 磁盘存储

## 1. 什么是 SSTable？

**SSTable (Sorted String Table)** 是 LSM Tree 中存储在磁盘上的不可变有序文件。它是 MemTable 刷盘后的产物，也是数据持久化的最终形态。

当 MemTable 达到大小阈值（如 64MB）时，会被"冻结"并转交给后台线程。后台线程将 MemTable 中的数据按顺序写入磁盘，生成一个新的 SSTable 文件。

> **命名由来**: "Sorted String Table" 这个名字源自 Google Bigtable 的论文，意味着它是一个存储有序字符串对 (Key-Value) 的表。

---

## 2. SSTable 核心特性

### 2.1 不可变性 (Immutability)

SSTable 一旦写入磁盘，就**永远不会被修改**。

- **并发简单**: 读取操作不需要加锁，因为没有写操作会修改文件内容。
- **缓存友好**: 操作系统和应用层可以放心地缓存文件内容。
- **备份容易**: 备份只需硬链接 (Hard Link) 或直接拷贝文件，无需担心数据不一致。

### 2.2 有序性 (Sorted)

所有键值对在文件中严格按键的字典序排列。

- **二分查找**: 可以在文件中进行高效的二分查找 (O(log N))。
- **范围查询**: 由于数据有序，扫描某个范围的数据 (Range Scan) 非常高效，本质上是顺序读。
- **归并排序**: 在 Compaction 阶段，合并多个 SSTable 就像归并排序 (Merge Sort) 一样高效。

### 2.3 自包含性 (Self-contained)

每个 SSTable 文件都是一个独立的单元，包含：

- **数据块 (Data Blocks)**: 实际的 Key-Value 数据。
- **索引块 (Index Blocks)**: 快速定位数据块的索引。
- **布隆过滤器 (Bloom Filter)**: 快速判断键是否不存在。
- **元数据 (Footer/Meta)**: 包含版本号、数据统计、校验和 (CRC) 等。

---

## 3. 文件格式设计

为了支持高效的随机读取和范围扫描，现代 SSTable (如 RocksDB) 通常采用基于**块 (Block)** 的存储格式：

```text
┌─────────────────────────────────────────────────────────────┐
│                    SSTable 文件物理布局                      │
├─────────────────────────────────────────────────────────────┤
│  [Data Block 0]  (存储 key "a" 到 "f")                       │
│  [Data Block 1]  (存储 key "g" 到 "m")                       │
│  ...                                                        │
│  [Data Block N]  (存储 key "x" 到 "z")                       │
├─────────────────────────────────────────────────────────────┤
│  [Meta Block: Bloom Filter] (全量数据的布隆过滤器)             │
├─────────────────────────────────────────────────────────────┤
│  [Index Block]   (稀疏索引: 记录每个 Data Block 的起始 Key)    │
├─────────────────────────────────────────────────────────────┤
│  [Footer]        (文件尾部: 指向 Index/Meta Block 的偏移量)    │
└─────────────────────────────────────────────────────────────┘
```

**为什么使用 Block？**

- **压缩**: 压缩算法（如 LZ4, Snappy）通常在 Block 级别进行。
- **IO 效率**: 每次读取至少读取一个 Block (如 4KB)，利用磁盘预读特性。
- **缓存**: Block Cache 以 Block 为单位缓存热点数据。

---

## 4. SSTable 实现解析

为了简化教学，我们的实现采用简化版的格式（不分 Block，但保留核心结构）：

```java
package com.brianxiadong.lsmtree;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Sorted String Table (SSTable) 实现
 * 磁盘上的有序不可变文件
 */
public class SSTable {
    // 文件路径
    private final String filePath;
    // 布隆过滤器：常驻内存，用于快速过滤
    private final BloomFilter bloomFilter;
    // 创建时间：用于确定数据的新旧程度
    private final long creationTime;

    // 稀疏索引 (内存中)：记录每隔 N 个条目的 Key 和文件偏移量 (简化版实现略)
    // private final TreeMap<String, Long> sparseIndex;

    // 构造函数：从内存数据创建 SSTable (Flush 过程)
    public SSTable(String filePath, List<KeyValue> sortedData) throws IOException {
        this.filePath = filePath;
        this.creationTime = System.currentTimeMillis();

        // 1. 构建布隆过滤器
        this.bloomFilter = new BloomFilter(sortedData.size(), 0.01);

        // 2. 写入文件 (数据 + 元数据)
        writeToFile(sortedData);
    }

    /**
     * 加载已存在的 SSTable (启动恢复过程)
     */
    public SSTable(String filePath) throws IOException {
        this.filePath = filePath;
        this.creationTime = Files.getLastModifiedTime(Paths.get(filePath)).toMillis();

        // 临时初始化，稍后从文件读取重建
        this.bloomFilter = new BloomFilter(1000, 0.01);

        // 从文件尾部或特定区域加载元数据
        rebuildBloomFilter();
    }
}
```

### 4.1 核心方法分析

#### 4.1.1 写入文件 (writeToFile)

```java
private void writeToFile(List<KeyValue> sortedData) throws IOException {
    // 使用 BufferedOutputStream 减少系统调用次数，提高吞吐量
    try (DataOutputStream dos = new DataOutputStream(
            new BufferedOutputStream(new FileOutputStream(filePath)))) {

        // 1. Header: 写入条目数量 (用于预分配内存或循环控制)
        dos.writeInt(sortedData.size());

        // 2. Data Region: 顺序写入所有 KV
        for (KeyValue kv : sortedData) {
            // 更新内存中的布隆过滤器
            bloomFilter.add(kv.getKey());

            // 写入 KV 数据
            dos.writeUTF(kv.getKey());
            dos.writeBoolean(kv.isDeleted());
            if (!kv.isDeleted()) {
                dos.writeUTF(kv.getValue());
            }
            dos.writeLong(kv.getTimestamp());
        }

        // 3. Meta Region: 写入布隆过滤器数据
        // 在实际系统中，布隆过滤器通常序列化后追加到文件尾部
        // 这里为了简化，假设在内存中重建或单独存储
    }
}
```

**优化点**:

- **Buffered IO**: 必须使用缓冲流，否则每个字段的写入都会触发一次 syscall，性能极差。
- **DataOutputStream**: 提供了便捷的 primitive type 写入方法，且格式紧凑。

#### 4.1.2 重建布隆过滤器 (rebuildBloomFilter)

```java
private void rebuildBloomFilter() throws IOException {
    // 实际生产中，布隆过滤器是直接从文件 Meta Block 读取的，速度极快
    // 这里演示的是"全表扫描重建" (仅用于教学或无 Meta Block 的情况)
    try (DataInputStream dis = new DataInputStream(
            new BufferedInputStream(new FileInputStream(filePath)))) {

        int totalEntries = dis.readInt();

        for (int i = 0; i < totalEntries; i++) {
            String key = dis.readUTF();
            boolean deleted = dis.readBoolean();

            // 跳过 Value 和 Timestamp，只关心 Key
            if (!deleted) {
                dis.readUTF();
            }
            dis.readLong();

            bloomFilter.add(key);
        }
    }
}
```

#### 4.1.3 查询操作 (get)

这是 LSM Tree 读取路径中**最耗时**的部分（涉及磁盘 IO）。

```java
public String get(String key) {
    // 1. 第一道防线：布隆过滤器 (内存操作)
    // 如果布隆过滤器说"不存在"，那绝对不存在，直接返回。
    // 这步操作挡住了绝大多数无效查询。
    if (!bloomFilter.mightContain(key)) {
        return null;
    }

    // 2. 第二道防线：稀疏索引 (Sparse Index)
    // (代码略) 在内存中查找 Key 可能存在的 Block 范围，避免全文件扫描。

    // 3. 磁盘读取 (Disk IO)
    // 如果没有稀疏索引，只能全表扫描 (性能很差，仅用于教学演示)
    try (DataInputStream dis = new DataInputStream(
            new BufferedInputStream(new FileInputStream(filePath)))) {

        int totalEntries = dis.readInt();

        // 顺序扫描 (O(N)) -> 实际应优化为 Block 定位 + 二分查找 (O(log N))
        for (int i = 0; i < totalEntries; i++) {
            String currentKey = dis.readUTF();
            boolean deleted = dis.readBoolean();
            String value = null;
            if (!deleted) {
                value = dis.readUTF();
            }
            long timestamp = dis.readLong();

            // 找到目标 Key
            if (currentKey.equals(key)) {
                return deleted ? null : value;
            }

            // 关键优化：提前终止
            // 因为文件是有序的，一旦遇到 currentKey > key，说明后面肯定没有了
            if (currentKey.compareTo(key) > 0) {
                break;
            }
        }
    } catch (IOException e) {
        e.printStackTrace();
    }

    return null;
}
```

#### 4.1.4 批量读取与文件清理

```java
/**
 * 获取所有键值对（用于 Compaction）
 * 这是一个昂贵的操作，通常只在后台合并线程中调用
 */
public List<KeyValue> getAllEntries() throws IOException {
    List<KeyValue> entries = new ArrayList<>();

    try (DataInputStream dis = new DataInputStream(
            new BufferedInputStream(new FileInputStream(filePath)))) {

        int totalEntries = dis.readInt();

        for (int i = 0; i < totalEntries; i++) {
            String key = dis.readUTF();
            boolean deleted = dis.readBoolean();
            String value = null;
            if (!deleted) {
                value = dis.readUTF();
            }
            long timestamp = dis.readLong();

            entries.add(new KeyValue(key, value, timestamp, deleted));
        }
    }
    return entries;
}

/**
 * 物理删除文件
 * 当 SSTable 被合并产生新文件后，旧文件可以安全删除
 */
public void delete() throws IOException {
    Files.deleteIfExists(Paths.get(filePath));
}
```

---

## 5. 小结

SSTable 的设计精髓在于**静态优化**。既然文件不可变，我们就可以花费一次性的计算成本（构建索引、布隆过滤器、压缩），来换取未来无数次的高效读取。

1. **不可变性**: 使得并发读取、缓存管理和备份变得极其简单。
2. **有序性**: 奠定了二分查找和范围查询的基础。
3. **分块存储**: 平衡了索引大小和读取粒度，支持了压缩和缓存。

---

## 6. 思考题

1. **稀疏索引**: 为什么 SSTable 的索引通常是稀疏的（每隔 N 个 Key 存一个），而不是密集的（存所有 Key）？这与 B+ 树的叶子节点索引有什么区别？
2. **数据压缩**: 为什么在 SSTable 中使用块压缩（如 LZ4）比单条记录压缩效果更好？
3. **IO 优化**: 在进行 Compaction 时，如何利用 `mmap` (内存映射文件) 来进一步提升文件读写性能？
