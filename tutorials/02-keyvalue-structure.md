# 第2章：KeyValue 数据结构

## 1. 为什么需要 KeyValue 结构？

在 LSM Tree 中，我们不能简单地像 `HashMap` 那样存储原始的键值对，因为 LSM Tree 的核心机制是**追加写 (Append-only)**。这意味着我们无法直接修改磁盘上已有的数据。为了支持这种模式，我们需要处理以下几个关键问题：

- **时间版本控制 (Versioning)**: 当用户更新一个键的值时，我们实际上是写入了一条新的记录。系统需要能够区分哪个版本是最新的。
- **删除语义 (Deletion Semantics)**: 由于不能物理删除磁盘上的数据，我们需要一种机制来标记"逻辑删除"。
- **数据完整性 (Integrity)**: 在系统崩溃恢复时，需要确保每一条记录都是完整的，未被截断。

因此，我们设计了 `KeyValue` 类作为 LSM Tree 的基础原子单元。所有的写入、更新、删除操作最终都会转化为一个 `KeyValue` 对象。

> **核心洞察**: LSM Tree 实际上是一个"操作日志"的集合。数据库中的当前状态，是由这些操作日志按时间顺序回放（或合并）产生的结果。

---

## 2. KeyValue 类设计

`KeyValue` 类的实现如下。它被设计为不可变对象，这极大简化了并发编程的复杂度。

```java
package com.brianxiadong.lsmtree;

/**
 * LSM Tree 中的键值对数据结构
 * 包含键、值和时间戳信息
 * 实现了 Comparable 接口以便在 MemTable (跳表) 和 SSTable 中进行排序
 */
public class KeyValue implements Comparable<KeyValue> {
    // 键字段：存储数据的唯一标识符，不可变
    private final String key;
    // 值字段：存储实际数据内容，删除时为 null
    private final String value;
    // 时间戳：记录数据写入的时间，用于版本控制 (Last-Write-Wins)
    private final long timestamp;
    // 删除标记：标识该记录是否为删除操作（墓碑 Tombstone）
    private final boolean deleted;

    // 构造函数：创建正常的键值对
    public KeyValue(String key, String value) {
        this(key, value, System.currentTimeMillis(), false);  // 调用完整构造函数
    }

    // 完整构造函数：用于创建特定状态的 KeyValue 对象
    public KeyValue(String key, String value, long timestamp, boolean deleted) {
        this.key = key;              // 设置键
        this.value = value;          // 设置值（可能为 null）
        this.timestamp = timestamp;  // 设置指定的时间戳
        this.deleted = deleted;      // 设置删除标记
    }

    /**
     * 工厂方法：创建删除标记的键值对
     * 这种特殊的记录被称为"墓碑" (Tombstone)
     */
    public static KeyValue createTombstone(String key) {
        // 创建一个删除标记，值为 null，标记为已删除
        return new KeyValue(key, null, System.currentTimeMillis(), true);
    }

    // 获取键的方法
    public String getKey() {
        return key;
    }

    // 获取值的方法
    public String getValue() {
        return value;
    }

    // 获取时间戳的方法
    public long getTimestamp() {
        return timestamp;
    }

    // 检查是否为删除标记的方法
    public boolean isDeleted() {
        return deleted;
    }

    // 实现 Comparable 接口，用于排序
    // 排序规则对于 LSM Tree 的正确性至关重要
    @Override
    public int compareTo(KeyValue other) {
        int keyCompare = this.key.compareTo(other.key);    // 1. 首先按键进行字典序排序
        if (keyCompare != 0) {
            return keyCompare;                             // 键不同，返回键的比较结果
        }
        // 2. 如果键相同，按时间戳降序排列（新的在前）
        // 这样在查找时，第一个找到的总是最新版本
        return Long.compare(other.timestamp, this.timestamp);
    }

    // 重写 toString 方法，便于调试和日志记录
    @Override
    public String toString() {
        return String.format("KeyValue{key='%s', value='%s', timestamp=%d, deleted=%s}",
                key, value, timestamp, deleted);
    }
}
```

**代码解释**: `KeyValue` 类是 LSM Tree 的核心数据结构。

- **Immutable**: 使用 `final` 关键字确保对象的不可变性，这意味着在多线程环境下传递 `KeyValue` 对象是绝对安全的，无需额外的同步开销。
- **Comparable**: `compareTo` 方法定义了数据的物理存储顺序。通过让较新的时间戳排在前面，我们在查询时一旦找到匹配的键，就可以立即返回（因为它一定是最新的），而无需继续向后查找。

---

## 3. 核心概念详解

### 3.1 时间戳 (Timestamp) 与版本控制

**作用**: 记录数据写入的时间，用于实现 **LWW (Last-Write-Wins)** 冲突解决策略。

在 LSM Tree 中，同一个键可能存在多个版本的数据分散在不同的层级（MemTable, Level 0, Level 1...）。时间戳是区分这些版本的唯一依据。

```java
// 时间戳的使用场景
KeyValue kv1 = new KeyValue("user:1", "Alice");  // 版本 1, timestamp: T1
Thread.sleep(1);
KeyValue kv2 = new KeyValue("user:1", "Bob");     // 版本 2, timestamp: T2 (T2 > T1)

// 查询逻辑：
// 假设 kv2 在 MemTable，kv1 在 SSTable
// 系统会先查到 kv2，发现它的时间戳 T2 > T1，因此返回 "Bob"
assert kv2.getTimestamp() > kv1.getTimestamp();
```

**为什么使用时间戳？**

- **版本控制**: 允许同一键的多个历史版本共存。
- **冲突解决**: 在合并（Compaction）过程中，如果有多个记录具有相同的键，系统保留时间戳最大的那个。
- **调试追踪**: 便于问题排查，知道数据是何时被修改的。

### 3.2 删除标记 (Tombstone)

在 LSM Tree 中，我们无法"删除"一条记录，只能"覆盖"它。删除操作本质上是写入一条特殊的记录，称为**墓碑 (Tombstone)**。

```java
// 正常的键值对
KeyValue normalKV = new KeyValue("user:1", "Alice");
assert !normalKV.isDeleted();

// 删除标记（墓碑）
// 这条记录告诉系统："user:1" 这个键在此时刻被删除了
KeyValue tombstone = KeyValue.createTombstone("user:1");
assert tombstone.isDeleted();
assert tombstone.getValue() == null;
```

**墓碑的工作原理**:
当查询 "user:1" 时，如果系统首先遇到了 `tombstone`（即它的时间戳比其他有效数据都要新），系统就会停止查找并返回 `null`，表示该数据不存在。

**墓碑的生命周期**:

1. **诞生**: 用户调用 `delete("key")`，墓碑被写入 MemTable 和 WAL。
2. **持久化**: 墓碑随 MemTable 刷盘进入 SSTable。
3. **生效**: 在查询时，它屏蔽了所有时间戳早于它的同名键。
4. **消亡**: 只有在 Compaction 阶段，当系统确认**不再有**比该墓碑更早的数据版本存在时，该墓碑才会被物理丢弃（回收空间）。

### 3.3 不可变性 (Immutability)

`KeyValue` 对象一旦创建，其状态就永久固定。

**不可变性的好处**:

- **线程安全**: 多个读线程可以并发访问同一个 `KeyValue` 对象而无需加锁。
- **缓存友好**: 可以放心地将 `KeyValue` 对象放入缓存（如 Block Cache），不用担心底层数据被修改导致缓存不一致。
- **引用传递**: 在方法间传递时只需传递引用，无需深拷贝。

---

## 4. 实际应用示例

### 4.1 基本 CRUD 操作

LSM Tree 的所有操作（增删改）在底层都是**追加 (Append)** 一个新的 `KeyValue` 对象。

```java
public class KeyValueExample {
    public static void main(String[] args) {
        // 1. 创建数据
        // 此时系统中有一条记录: {key="user:1001", value="Alice", ts=100}
        KeyValue create = new KeyValue("user:1001", "Alice");
        System.out.printf("创建: %s = %s (时间: %d)%n",
                         create.getKey(), create.getValue(), create.getTimestamp());

        // 2. 更新数据
        // 此时系统中有两条记录，读取时会优先读到 ts=101 的记录
        // {key="user:1001", value="Alice Smith", ts=101}
        // {key="user:1001", value="Alice", ts=100}
        KeyValue update = new KeyValue("user:1001", "Alice Smith");
        System.out.printf("更新: %s = %s (时间: %d)%n",
                         update.getKey(), update.getValue(), update.getTimestamp());

        // 3. 删除数据
        // 此时系统中有三条记录，墓碑屏蔽了之前的所有记录
        // {key="user:1001", type=TOMBSTONE, ts=102}
        // {key="user:1001", value="Alice Smith", ts=101}
        // {key="user:1001", value="Alice", ts=100}
        KeyValue delete = KeyValue.createTombstone("user:1001");
        System.out.printf("删除: %s (删除标记: %b, 时间: %d)%n",
                         delete.getKey(), delete.isDeleted(), delete.getTimestamp());
    }
}
```

### 4.2 版本控制示例

这个示例展示了如何在读取时通过排序来获取最新版本。

```java
public class VersionControlExample {
    public static void main(String[] args) throws InterruptedException {
        List<KeyValue> versions = new ArrayList<>();

        // 模拟写入序列
        versions.add(new KeyValue("config", "v1.0"));      // ts=100
        Thread.sleep(10);
        versions.add(new KeyValue("config", "v1.1"));      // ts=110
        Thread.sleep(10);
        versions.add(KeyValue.createTombstone("config"));  // ts=120

        // 模拟查询过程：按 KeyValue 的 compareTo 规则排序
        // 规则：Key 升序 -> Timestamp 降序
        versions.sort(KeyValue::compareTo);
        // 排序后: [Tombstone(ts=120), v1.1(ts=110), v1.0(ts=100)]

        KeyValue latest = versions.get(0);  // 获取第一个元素即为最新版本
        if (latest.isDeleted()) {
            System.out.println("键 'config' 已被删除");
        } else {
            System.out.println("最新值: " + latest.getValue());
        }
    }
}
```

### 4.3 压缩场景模拟 (Compaction)

压缩是 LSM Tree 释放空间的关键。它的核心逻辑是：对于同一个 Key，只保留最新的那个版本。

```java
public class CompactionExample {
    // 简化的压缩逻辑：输入一堆乱序的 KV，输出去重后的 KV
    public static List<KeyValue> compactKeyValues(List<KeyValue> input) {
        // 1. 分组：将相同 Key 的数据聚在一起
        Map<String, List<KeyValue>> grouped = input.stream()
            .collect(Collectors.groupingBy(KeyValue::getKey));

        List<KeyValue> result = new ArrayList<>();

        for (Map.Entry<String, List<KeyValue>> entry : grouped.entrySet()) {
            List<KeyValue> versions = entry.getValue();

            // 2. 排序：找到每个 Key 的最新版本
            versions.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

            KeyValue latest = versions.get(0);

            // 3. 过滤：
            // 如果最新版本是正常数据 -> 保留
            // 如果最新版本是墓碑 -> 在这里我们假设可以安全丢弃（实际系统中需要更复杂的判断）
            if (!latest.isDeleted()) {
                result.add(latest);
            }
        }

        return result;
    }
}
```

---

## 5. 设计考虑与优化

### 5.1 内存效率 (Object Overhead)

在 Java 中，对象是有额外开销的。一个 `KeyValue` 对象不仅仅是字段大小的总和。

```java
// 估算 KeyValue 内存占用 (64位 JVM, 开启 Compressed Oops)
public static long estimateMemoryUsage(String key, String value) {
    // 1. 对象头 (Object Header): 12 bytes
    long objectHeader = 12;

    // 2. 引用字段 (References): 4 * 4 bytes = 16 bytes
    // (String key, String value, long timestamp, boolean deleted 实际上会有填充)
    // 注意：boolean 在对象中通常占用 1 byte，但由于对齐可能会更多

    // 3. 原始数据类型
    long primitiveData = 8 (long) + 1 (boolean);

    // 4. 填充 (Padding): 补齐到 8 的倍数

    // 5. 字符串本身的开销 (String 对象头 + char[] 数组)
    long stringSize = estimateStringSize(key) + estimateStringSize(value);

    return objectOverhead + stringSize;
}
```

**优化建议**:

- **字符串池化 (String Interning)**: 对于重复率高的 Key（如 "user:type"），使用 `String.intern()` 或自定义池化可以节省大量内存。
- **扁平化存储**: 在生产级实现中（如 RocksDB 的 Java 版），通常尽量避免创建海量的小对象，而是使用 `ByteBuffer` 或堆外内存 (Off-heap) 紧凑存储数据。

### 5.2 序列化考虑

`KeyValue` 需要在内存和磁盘之间转换。

```java
// 简单的文本序列化：key|value|timestamp|deleted
// 优点：人类可读，易于调试
// 缺点：包含分隔符的数据需要转义，空间效率低
public String serialize() {
    return String.format("%s|%s|%d|%b", key, value, timestamp, deleted);
}

// 生产环境建议：二进制序列化
// [Key Len(4)][Key Bytes][Value Len(4)][Value Bytes][Timestamp(8)][Type(1)]
// 优点：紧凑，无需转义，解析快
```

---

## 6. 常见问题

### 6.1 为什么不直接物理删除？

**问题**: 为什么 `delete` 操作要插入墓碑而不是直接去磁盘文件中删除那行数据？

**答案**:

1. **顺序写约束**: SSTable 文件是追加写入的，且一旦写完就不可变。我们无法修改文件中间的某一行。
2. **性能**: 物理删除需要重写整个文件，这会产生巨大的写放大。插入墓碑只是追加一条小记录，性能是 O(1)。
3. **一致性**: 墓碑作为一条普通的记录参与排序和合并，天然保证了多层级数据的一致性。

### 6.2 墓碑何时被清理？

墓碑不能随意丢弃，否则旧数据可能会"复活"。

**清理条件**:
只有当 Compact 过程确认**所有更底层 (Level N+1)** 的 SSTable 中都不包含该 Key 的旧版本时，当前层 (Level N) 的墓碑才能被安全丢弃。

### 6.3 系统时钟回拨怎么办？

**问题**: 如果服务器时钟回拨，新写入的数据时间戳可能小于旧数据，导致数据丢失（旧数据覆盖了新数据）。

**解决方案**:

1. **逻辑时钟**: 不完全依赖物理时间，而是使用单调递增的序列号 (Sequence Number)。RocksDB 就使用 Sequence Number 作为版本控制的主要依据。
2. **混合时钟**: 结合物理时间和逻辑计数器，确保时间戳单调递增。

```java
private static final AtomicLong SEQUENCE = new AtomicLong(0);

// 确保生成的 ID 永远单调递增
public long nextId() {
    return System.currentTimeMillis() << 20 | (SEQUENCE.getAndIncrement() & 0xFFFFF);
}
```

---

## 7. 思考题

1. **序列号 vs 时间戳**: 如果我们使用自增序列号代替时间戳，会有什么优缺点？（提示：分布式环境下的时钟同步问题）
2. **墓碑累积**: 如果一个恶意用户疯狂对同一个 Key 进行创建-删除-创建-删除循环，会对 LSM Tree 造成什么影响？如何缓解？
3. **Value 为空**: 如果允许 Value 为 `null`，我们如何区分"Value 是 null"和"这是一个墓碑"？

**下一章预告**: 我们将深入 MemTable，看看这些 KeyValue 对象是如何在内存中被高效组织和并发访问的。
