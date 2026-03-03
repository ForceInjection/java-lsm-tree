# 第3章：MemTable 内存表

## 1. 什么是 MemTable？

**MemTable (内存表)** 是 LSM Tree 中驻留在内存中的数据结构，它扮演着"写入缓冲区"和"排序缓冲区"的双重角色。

所有新的写入操作（PUT/DELETE）首先都会进入 MemTable。当 MemTable 达到预设的容量阈值（如 64MB）时，它会被冻结并转换为 **Immutable MemTable**，随后由后台线程刷新到磁盘生成 SSTable。

> **核心作用**: MemTable 将来自客户端的随机写入请求在内存中进行排序，从而将后续的磁盘写入操作转换为高性能的顺序写 (Sequential Write)。

---

## 2. 为什么选择跳表？

在 LSM Tree 的 MemTable 实现中，我们选择了**跳表 (Skip List)** 作为底层数据结构，而不是红黑树或 AVL 树。这并非偶然，而是基于工程实现的深思熟虑。

### 2.1 数据结构对比

| 数据结构             | 插入性能     | 查找性能     | 范围查询 | 并发实现难度                               | 空间利用率      |
| :------------------- | :----------- | :----------- | :------- | :----------------------------------------- | :-------------- |
| **红黑树 (RB-Tree)** | O(log N)     | O(log N)     | O(N)     | **极难**：旋转操作涉及多个节点，锁竞争严重 | 高              |
| **B+ 树**            | O(log N)     | O(log N)     | O(N)     | **中等**：虽然有锁优化，但页分裂仍需锁     | 一般            |
| **跳表 (Skip List)** | **O(log N)** | **O(log N)** | **O(N)** | **简单**：基于 CAS 的无锁实现，局部性好    | 一般 (指针开销) |

### 2.2 跳表的优势

1. **并发友好 (Lock-Free)**: 跳表的插入和删除操作只需要修改局部的指针，非常适合通过 CAS (Compare-And-Swap) 实现无锁并发。Java 的 `ConcurrentSkipListMap` 就是典范。
2. **实现简单**: 相比于红黑树复杂的旋转和再平衡逻辑，跳表的算法更加直观，代码量通常只有红黑树的 1/3。
3. **缓存友好**: 跳表的节点在内存中通常是分散的，这似乎对缓存不友好？但实际上，跳表的高层索引提供了很好的跨度，使得查找过程中的内存访问次数较少。
4. **范围查询**: 跳表底层的链表天然有序，非常适合执行 Range Scan 操作。

---

## 3. MemTable 实现分析

让我们深入分析 MemTable 的实现：

```java
package com.brianxiadong.lsmtree;

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.List;
import java.util.ArrayList;

public class MemTable {
    /**
     * 底层存储结构
     * 使用 ConcurrentSkipListMap 保证线程安全和有序性
     * Key: String (用户键)
     * Value: KeyValue (封装了值、时间戳、删除标记的完整对象)
     */
    private final ConcurrentSkipListMap<String, KeyValue> data;

    // 最大容量阈值 (字节数)，超过此值将触发刷盘
    private final int maxSize;

    // 当前估算大小，使用 volatile 确保多线程可见性
    // 注意：这里统计的是近似值，为了性能放弃了强一致的精确计数
    private volatile int currentSize;

    // 构造函数：初始化 MemTable
    public MemTable(int maxSize) {
        this.data = new ConcurrentSkipListMap<>();
        this.maxSize = maxSize;
        this.currentSize = 0;
    }

    // 核心方法...
}
```

**代码解释**:

- `ConcurrentSkipListMap`: 这是 Java 标准库提供的高性能并发跳表实现。它保证了在多线程写入时的线程安全，且无需全局锁。
- `volatile currentSize`: 在高并发写入时，我们需要快速判断是否需要刷盘。使用 `volatile` 变量进行计数是一种低开销的方案，虽然在高并发下可能存在轻微的计数偏差（"脏读"），但这对于触发刷盘阈值来说是可以接受的。

---

## 4. 跳表原理深入

### 4.1 跳表结构图解

跳表是一种概率型数据结构，本质上是**多层链表**。

```text
Level 3: [1]------------------------------------->[17]---------> NULL
          |                                        |
Level 2: [1]------------->[9]-------------------->[17]---------> NULL
          |                |                        |
Level 1: [1]------>[4]---->[9]---------->[12]---->[17]---------> NULL
          |         |       |              |        |
Level 0: [1]------>[4]---->[9]------>[10]->[12]---->[17]->[19]-> NULL
```

**查找路径 (Target = 12)**:

1. **L3**: 1 -> 17 (17 > 12，过大，下沉到 L2)
2. **L2**: 1 -> 9 -> 17 (17 > 12，过大，下沉到 L1)
3. **L1**: 9 -> 12 (命中！或者继续下沉确认)

**关键机制**:

- **层级提升**: 当插入新节点时，通过抛硬币（随机函数）决定该节点是否"晋升"到上一层索引。晋升概率通常为 1/2 或 1/4。
- **空间换时间**: 通过维护额外的索引指针，换取了 O(log N) 的查找效率。

### 4.2 查找算法

```java
// 简化的跳表查找逻辑
public KeyValue search(String key) {
    Node current = head;

    // 从最高层开始向下搜索
    for (int level = maxLevel; level >= 0; level--) {
        // 在当前层尽可能向右移动
        while (current.forward[level] != null &&
               current.forward[level].key.compareTo(key) < 0) {
            current = current.forward[level];
        }
    }

    // 此时 current 是小于 key 的最大节点
    // 检查 level 0 的下一个节点
    current = current.forward[0];
    if (current != null && current.key.equals(key)) {
        return current.value;
    }
    return null;
}
```

### 4.3 插入算法与 CAS

在并发环境下，插入操作是最复杂的。`ConcurrentSkipListMap` 使用 CAS (Compare-And-Swap) 来保证原子性。

**CAS 插入流程**:

1. 找到待插入位置的前驱节点 `pred` 和后继节点 `succ`。
2. 创建新节点 `newNode`，令 `newNode.next = succ`。
3. **CAS 操作**: 尝试将 `pred.next` 从 `succ` 修改为 `newNode`。
   - 如果成功：插入完成。
   - 如果失败（说明其他线程修改了 `pred.next`）：重新读取，重试步骤 1（自旋）。

这种无锁设计避免了线程阻塞和上下文切换，在高并发场景下吞吐量极高。

---

## 5. MemTable 核心操作

### 5.1 写入操作 (PUT)

```java
public void put(String key, String value) {
    // 1. 封装数据：包含时间戳
    KeyValue kv = new KeyValue(key, value);

    // 2. 写入跳表：put 操作是原子的
    // 如果 key 已存在，put 会覆盖旧值并返回旧对象
    KeyValue oldValue = data.put(key, kv);

    // 3. 更新容量计数
    // 只有新增 key 时才增加计数？不完全是。
    // 在实际生产系统中，应该计算 key+value 的字节大小增量
    if (oldValue == null) {
        currentSize += estimateSize(key, value);
    } else {
        // 如果是更新，增加的大小是新值与旧值的差额
        currentSize += (estimateSize(key, value) - estimateSize(key, oldValue.getValue()));
    }
}
```

**注意**: 这里展示的 `currentSize` 更新逻辑进行了简化。在严格实现中，即使是覆盖写，也可能导致内存占用变化，因此需要精确计算字节数。

### 5.2 读取操作 (GET)

```java
public String get(String key) {
    // 从跳表中查找
    KeyValue kv = data.get(key);

    // 检查结果
    if (kv == null) {
        return null; // 内存表中没有
    }

    // 检查墓碑标记
    if (kv.isDeleted()) {
        return null; // 显式标记为删除，返回 null (屏蔽旧数据)
    }

    return kv.getValue();
}
```

### 5.3 删除操作 (DELETE)

```java
public void delete(String key) {
    // 创建墓碑：value 为 null，deleted = true
    KeyValue tombstone = KeyValue.createTombstone(key);

    // 将墓碑插入跳表，覆盖原有数据
    KeyValue oldValue = data.put(key, tombstone);

    // 墓碑本身也占用内存空间，需要计入
    if (oldValue == null) {
        currentSize += estimateSize(key, null);
    }
}
```

### 5.4 刷盘检查与冻结

```java
public boolean shouldFlush() {
    return currentSize >= maxSize;
}

// 冻结操作：返回当前数据，并重置 MemTable（由上层调用者控制）
// 实际上，通常是创建一个新的 MemTable 实例替换旧的
```

**刷盘流程**:

1. **检查**: 每次写入后检查 `shouldFlush()`。
2. **冻结**: 如果需要刷盘，将当前 `activeMemTable` 指针指向一个新的空 MemTable。
3. **转换**: 旧的 `activeMemTable` 变为 `immutableMemTable`。
4. **提交**: 将 `immutableMemTable` 提交给后台 Flush 线程。

---

## 6. 并发控制深入

### 6.1 为什么不使用 synchronized?

如果在 `put` 方法上加 `synchronized` 锁：

- **优点**: 实现极其简单，绝对线程安全。
- **缺点**: 所有写线程串行化。在多核 CPU 下，只有一个核在工作，其他线程都在阻塞等待，无法利用多核优势。

### 6.2 CAS 的 ABA 问题

在跳表实现中，CAS 可能会遇到 ABA 问题（值从 A 变 B 又变回 A，CAS 认为没变）。Java 的 `ConcurrentSkipListMap` 通过节点状态标记和版本号等机制解决了这个问题，使用者无需担心。

---

## 7. 小结

MemTable 是 LSM Tree 高性能写入的基石：

1. **写缓冲**: 吸收随机写，转化为批量写。
2. **内存排序**: 利用跳表在内存中维护有序性，为磁盘顺序写做准备。
3. **无锁并发**: 利用 CAS 机制支持高吞吐量的并发写入。

---

## 8. 思考题

1. **内存开销**: 跳表维持多层索引指针，会带来多大的额外内存开销？相比于 B+ 树的页结构，谁的空间利用率更高？
2. **动态调整**: 如果写入速度过快，导致 Immutable MemTable 来不及刷盘（内存爆满），系统应该如何进行流量控制 (Write Stall)？
3. **替代方案**: 在某些极端的读多写少场景下，MemTable 是否可以用平衡树代替跳表以获得微小的读性能提升？

**下一章预告**: 当 MemTable 满载后，数据将流向何方？我们将深入 SSTable 的磁盘存储结构。
