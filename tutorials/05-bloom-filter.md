# 第5章：布隆过滤器

## 1. 什么是布隆过滤器？

**布隆过滤器 (Bloom Filter)** 是一种空间效率极高的概率型数据结构，由 Burton Howard Bloom 在 1970 年提出。它的核心用途是：**快速判断一个元素是否可能存在于集合中**。

它具有以下鲜明的特性：

- **无假阴性 (No False Negatives)**: 如果布隆过滤器说"元素不存在"，那么它**绝对不存在**。这是布隆过滤器能够安全优化查询的前提。
- **有假阳性 (False Positives)**: 如果布隆过滤器说"元素存在"，那么它**可能存在**，也可能不存在（误判）。
- **空间高效**: 相比于 `HashSet` 或 `HashMap` 需要存储元素本身，布隆过滤器只需要存储元素的哈希指纹（位数组），内存占用极小（通常每个元素仅需几比特）。
- **时间高效**: 无论是插入还是查询，时间复杂度都是 O(k)，其中 k 是哈希函数的数量，与集合大小无关。

---

## 2. 布隆过滤器在 LSM Tree 中的作用

在 LSM Tree 中，随着数据量的增长，磁盘上会生成大量的 SSTable 文件。如果每次读取都要去磁盘中检查 Key 是否存在，性能将极其低下。

布隆过滤器被作为 SSTable 的**伴生结构**（通常存储在 SSTable 文件的元数据区，加载时常驻内存），充当了"守门员"的角色：

```text
查询流程:
1. 检查 MemTable (内存查找，快)
2. 检查 Immutable MemTable (内存查找，快)
3. 准备检查磁盘 SSTable 文件...
   [布隆过滤器检查] -> Key "user:123" ?
       |
       +---> 结果: "不存在" -> 直接跳过该文件 (Saved Disk IO!)
       |
       +---> 结果: "可能存在" -> 读取磁盘文件索引块 -> 二分查找 -> 最终确认
```

> **性能数据**: 在合理的参数配置下（如 1% 误判率），布隆过滤器可以帮助我们规避 99% 的无效磁盘 I/O。对于"Key 不存在"的查询场景（如黑名单检查），性能提升尤为巨大。

---

## 3. 核心原理

布隆过滤器的底层是一个很长的**二进制位数组 (Bit Array)** 和一系列**哈希函数**。

### 3.1 工作机制图解

假设我们有一个长度为 16 的位数组，初始全为 0。

**步骤 1: 初始状态**。

| 0   | 1   | 2   | 3   | 4   | 5   | 6   | 7   | 8   | 9   | 10  | 11  | 12  | 13  | 14  | 15  |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 0   | 0   | 0   | 0   | 0   | 0   | 0   | 0   | 0   | 0   | 0   | 0   | 0   | 0   | 0   | 0   |

**步骤 2: 添加 "apple"**。

假设 3 个哈希函数计算结果为：`h1("apple")=3`, `h2("apple")=7`, `h3("apple")=12`。
我们将这 3 个位置标记为 1。

| 0   | 1   | 2   | 3     | 4   | 5   | 6   | 7     | 8   | 9   | 10  | 11  | 12    | 13  | 14  | 15  |
| --- | --- | --- | ----- | --- | --- | --- | ----- | --- | --- | --- | --- | ----- | --- | --- | --- |
| 0   | 0   | 0   | **1** | 0   | 0   | 0   | **1** | 0   | 0   | 0   | 0   | **1** | 0   | 0   | 0   |

**步骤 3: 添加 "banana"**。

假设计算结果为：`h1("banana")=1`, `h2("banana")=9`, `h3("banana")=15`。

| 0   | 1     | 2   | 3   | 4   | 5   | 6   | 7   | 8   | 9     | 10  | 11  | 12  | 13  | 14  | 15    |
| --- | ----- | --- | --- | --- | --- | --- | --- | --- | ----- | --- | --- | --- | --- | --- | ----- |
| 0   | **1** | 0   | 1   | 0   | 0   | 0   | 1   | 0   | **1** | 0   | 0   | 1   | 0   | 0   | **1** |

**步骤 4: 查询测试**。

- **查询 "apple"**: 计算 hash 得到 3, 7, 12。检查位数组，发现这 3 个位置全是 1。
  -> **结论**: "apple" **可能存在**。

- **查询 "cherry"**: 假设 hash 得到 2, 6, 10。检查位数组，发现位置 2 是 0。
  -> **结论**: "cherry" **绝对不存在**（因为如果它存在，位置 2 必然是 1）。

- **查询 "grape" (假阳性案例)**: 假设 hash 得到 1, 7, 9。
  - 位置 1 是 1 (由 "banana" 设置)
  - 位置 7 是 1 (由 "apple" 设置)
  - 位置 9 是 1 (由 "banana" 设置)
    -> **结论**: "grape" **可能存在**。但实际上它不存在！这就是**误判 (False Positive)**。

---

## 4. 布隆过滤器实现

### 4.1 核心实现

我们的实现使用了 `java.util.BitSet`，并采用了 **Double Hashing** 技术来模拟多个哈希函数。

```java
package com.brianxiadong.lsmtree;

import java.util.BitSet;

/**
 * 布隆过滤器实现
 * 用于快速判断键是否可能存在于 SSTable 中
 */
public class BloomFilter {
    private final BitSet bitSet;               // 位数组，存储布隆过滤器的位
    private final int size;                    // 位数组的大小 (m)
    private final int hashFunctions;           // 哈希函数的数量 (k)

    // 构造函数：根据预期元素数 (n) 和期望的假阳性概率 (p) 自动计算参数
    public BloomFilter(int expectedElements, double falsePositiveProbability) {
        // 公式: m = -n * ln(p) / (ln(2))^2
        // 例如：预期 100 万元素，1% 误判率，大约需要 9.6MB 内存
        this.size = (int) (-expectedElements * Math.log(falsePositiveProbability)
                / (Math.log(2) * Math.log(2)));

        // 公式: k = (m/n) * ln(2)
        // 例如：对于 1% 误判率，通常需要 7 个哈希函数
        this.hashFunctions = (int) (size * Math.log(2) / expectedElements);

        this.bitSet = new BitSet(size);        // 创建位数组
    }

    /**
     * 向布隆过滤器添加元素
     */
    public void add(String key) {
        for (int i = 0; i < hashFunctions; i++) {      // 使用 k 个哈希函数
            int hash = hash(key, i);                   // 计算第 i 个哈希值
            bitSet.set(Math.abs(hash % size));         // 设置对应位为 1
        }
    }

    /**
     * 检查元素是否可能存在
     * 返回 false -> 绝对不存在 (Safe to skip IO)
     * 返回 true  -> 可能存在 (Need check IO)
     */
    public boolean mightContain(String key) {
        for (int i = 0; i < hashFunctions; i++) {      // 检查 k 个哈希位置
            int hash = hash(key, i);                   // 计算第 i 个哈希值
            if (!bitSet.get(Math.abs(hash % size))) {  // 只要有一个位为 0
                return false;                          // 就可以断定元素绝对不存在
            }
        }
        return true;                                   // 所有位都为 1，可能存在
    }

    /**
     * 多重哈希函数实现
     * 使用 Double Hashing 技术避免实现多个独立的哈希函数
     * hash_i(x) = (hash1(x) + i * hash2(x)) % m
     */
    private int hash(String key, int i) {
        int hash1 = key.hashCode();                    // 基础哈希 1
        int hash2 = hash1 >>> 16;                      // 基础哈希 2 (利用高位)
        return hash1 + i * hash2;                      // 组合生成第 i 个哈希值
    }

    /**
     * 获取位数组序列化数据（用于持久化到 SSTable 文件）
     */
    public byte[] toByteArray() {
        return bitSet.toByteArray();
    }

    /**
     * 从字节数组恢复布隆过滤器（SSTable 加载时调用）
     */
    public static BloomFilter fromByteArray(byte[] data, int size, int hashFunctions) {
        // 创建一个空的过滤器实例
        BloomFilter filter = new BloomFilter(1000, 0.01); // 这里的参数仅为占位
        filter.bitSet.clear();

        // 恢复位数据
        BitSet restored = BitSet.valueOf(data);
        filter.bitSet.or(restored);

        return filter;
    }
}
```

**代码解析**：

- **参数自适应**: 构造函数中并没有让用户指定 `size`，而是让用户指定"预期数据量"和"可接受的误判率"。这是更友好的 API 设计。
- **Double Hashing**: 为了模拟 k 个独立的哈希函数，我们没有真的去写 k 个函数，而是用了 `hash1 + i * hash2` 的线性组合。数学上证明这在实际应用中效果足够好。

---

## 5. 小结

布隆过滤器是 LSM Tree "读写分离"设计思想的完美补充。LSM Tree 牺牲了一定的读取性能（需要读多层文件）来换取极致的写入性能，而布隆过滤器则通过极小的内存代价，补回了大部分的读取性能损失。

1. **空间**: 牺牲少量内存（位数组）。
2. **时间**: 换取了大量的磁盘 I/O 时间节省。
3. **权衡**: 通过调整参数 (m, k)，可以在内存占用和误判率之间自由权衡。

---

## 6. 思考题

1. **删除难题**: 为什么标准的布隆过滤器不支持删除操作？（提示：思考一下如果有两个元素映射到了同一个位，删除其中一个会发生什么？）
2. **误判率选择**: 对于一个缓存系统，误判率设为 1% 和 10% 有什么区别？如果查询未命中的代价非常高（比如穿透到慢速 DB），应该如何设置？
3. **性能退化**: 在什么极端场景下，布隆过滤器会失效（无法过滤任何文件），导致读取性能退化到最差？

**下一章预告**: 数据在内存中是易失的，如果断电了怎么办？我们将学习 WAL (Write-Ahead Log) 如何保障数据安全。
