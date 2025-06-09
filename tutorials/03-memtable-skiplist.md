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

# 第3章：MemTable 内存表

## 什么是MemTable？

**MemTable (内存表)** 是LSM Tree中接收所有写入操作的内存数据结构。它扮演着缓冲区的角色，将随机写入转换为顺序写入，是LSM Tree高性能的关键组件。

## 为什么选择跳表？

在LSM Tree的MemTable实现中，我们选择了**跳表 (Skip List)** 作为底层数据结构。让我们看看为什么：

### 数据结构对比

| 数据结构 | 插入 | 查找 | 删除 | 有序遍历 | 并发性能 |
|----------|------|------|------|----------|----------|
| 红黑树   | O(log n) | O(log n) | O(log n) | O(n) | 需要复杂锁 |
| B+树     | O(log n) | O(log n) | O(log n) | O(n) | 锁开销大 |
| **跳表** | **O(log n)** | **O(log n)** | **O(log n)** | **O(n)** | **并发友好** |

### 跳表的优势

1. **并发友好**: Java的`ConcurrentSkipListMap`提供了优秀的并发性能
2. **实现简单**: 相比红黑树，跳表实现更简单
3. **缓存友好**: 较好的空间局部性
4. **有序性**: 天然支持有序遍历

## MemTable 实现分析

让我们深入分析MemTable的实现：

```java
package com.brianxiadong.lsmtree;

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.List;
import java.util.ArrayList;

public class MemTable {
    // 使用并发跳表作为底层存储，键为String，值为KeyValue对象
    private final ConcurrentSkipListMap<String, KeyValue> data;
    // 最大容量阈值，超过此值将触发刷盘操作
    private final int maxSize;
    // 当前大小，使用volatile确保多线程可见性
    private volatile int currentSize;

    // 构造函数：初始化MemTable
    public MemTable(int maxSize) {
        this.data = new ConcurrentSkipListMap<>();  // 创建线程安全的跳表
        this.maxSize = maxSize;                     // 设置最大容量
        this.currentSize = 0;                       // 初始大小为0
    }
    
    // 核心方法...
}
```

**代码解释**: 这个MemTable类使用ConcurrentSkipListMap作为底层存储，它提供了线程安全的有序键值存储。`maxSize`字段控制何时触发刷盘操作，而`currentSize`使用volatile关键字确保在多线程环境下的可见性。

### 核心设计要点

1. **ConcurrentSkipListMap**: Java并发包提供的线程安全跳表实现
2. **volatile currentSize**: 确保大小检查的可见性
3. **immutable maxSize**: 刷盘触发阈值

## 跳表原理深入

### 跳表结构图解

```
跳表查找12的真实路径演示:

Level 3: [1]--------①水平-------->[17]---------> NULL
          |                        (17>12停止)
          |②下降
          ↓
Level 2: [1]---③水平--->[9]---④水平--->[17]-------> NULL
                        |             (17>12停止)  
                        |⑤下降到Level 1
                        ↓
Level 1: [1]-->[4]----->[9]------->[17]-->[25]---> NULL
                        |⑥检查      (下个是17>12，无需水平移动)
                        |⑦下降到Level 0  
                        ↓
Level 0: [1]-->[4]----->[9]--⑧找到-->[12🎯]-->[17]-->[25]---> NULL
                                     (找到目标!)

真实搜索路径: 1(L3) → 1(L2) → 9(L2) → 9(L1) → 9(L0) → 12(L0) ✓
关键洞察: Level 1并未被"跳过"，而是在此处做了快速判断后继续下降
跳表精髓: 通过多层索引快速"跳过"不必要的节点比较，而非跳过层级
```

**跳表结构说明**: 跳表通过多层索引实现快速查找。上层作为下层的"快速通道"，每个节点在多个层级上建立索引。查找时从顶层开始，利用稀疏索引快速逼近目标，然后逐层下降精确定位。

**查找12的完整路径(按编号顺序)**:
1. **①水平**: Level 3从1开始，向右到17 (17>12，停止水平移动)
2. **②下降**: 从Level 3的1下降到Level 2的1  
3. **③水平**: Level 2从1向右到9 (9<12，继续)
4. **④水平**: Level 2从9向右到17 (17>12，停止水平移动)
5. **⑤下降**: 从Level 2的9下降到Level 1的9
6. **Level 1检查**: 在Level 1的9处，下一个节点是17 (17>12，无需水平移动)
7. **继续下降**: 从Level 1的9下降到Level 0的9  
8. **⑥找到**: Level 0从9向右到12🎯 (找到目标！)

**为什么看起来"跳过"了Level 1？**
- 实际上算法会**逐层检查每一层**，不会真的跳过
- 在Level 1的节点9处，发现下一个节点是17>12，所以无需水平移动
- 但算法仍然会**在Level 1停留并做判断**，然后继续下降
- 这就是为什么跳表叫"Skip List"——它能"跳过"不必要的比较，而不是跳过层级

### 查找过程

1. **从最高层开始**: 从Level 3的头节点开始
2. **水平移动**: 在当前层向右移动，直到下一个节点 > 目标值
3. **向下移动**: 移动到下一层继续查找
4. **重复过程**: 直到找到目标或到达Level 0

**查找12的路径演示**:
- Level 3: 1 → 17 (17>12，下降)
- Level 2: 1 → 9 → 17 (17>12，下降) 
- Level 1: 1 → 4 → 9 → 17 (17>12，下降)
- Level 0: 1 → 4 → 9 → 12 ✓ (找到目标)

```java
// 跳表查找伪代码
public KeyValue search(String key) {
    Node current = head;  // 从头节点开始
    
    // 从最高层开始向下搜索
    for (int level = maxLevel; level >= 0; level--) {
        // 在当前层水平移动，寻找合适的位置
        while (current.forward[level] != null &&                    // 下一个节点存在
               current.forward[level].key.compareTo(key) < 0) {     // 且键值小于目标键
            current = current.forward[level];                       // 移动到下一个节点
        }
    }
    
    // 移动到Level 0的下一个节点检查是否找到目标
    current = current.forward[0];
    if (current != null && current.key.equals(key)) {  // 找到目标键
        return current.value;                           // 返回对应的值
    }
    return null;  // 未找到，返回null
}
```

**代码解释**: 这个搜索算法体现了跳表的核心思想：通过多层索引快速定位。从最高层开始，每层都尽可能向右移动，然后向下到下一层继续搜索。这种"跳跃"式的搜索方式大大减少了比较次数。

### 插入过程

```java
// 跳表插入伪代码
public void insert(String key, KeyValue value) {
    Node[] update = new Node[maxLevel + 1];  // 记录每层的插入位置
    Node current = head;                     // 从头节点开始
    
    // 找到每一层的插入位置
    for (int level = maxLevel; level >= 0; level--) {
        // 在当前层找到插入位置的前驱节点
        while (current.forward[level] != null && 
               current.forward[level].key.compareTo(key) < 0) {
            current = current.forward[level];       // 向右移动
        }
        update[level] = current;  // 记录该层的前驱节点
    }
    
    // 随机决定新节点的层数（跳表的概率特性）
    int newLevel = randomLevel();
    Node newNode = new Node(key, value, newLevel);  // 创建新节点
    
    // 在每一层插入新节点
    for (int level = 0; level <= newLevel; level++) {
        newNode.forward[level] = update[level].forward[level];  // 新节点指向后继
        update[level].forward[level] = newNode;                 // 前驱指向新节点
    }
}
```

**代码解释**: 插入操作分为两个阶段：首先找到各层的插入位置，然后执行实际插入。随机层数的选择是跳表的关键特性，它保证了跳表的平衡性。每个新节点都会在随机选择的多个层级上建立索引。

## MemTable 核心操作

### 1. 写入操作 (PUT)

```java
public void put(String key, String value) {
    KeyValue kv = new KeyValue(key, value);  // 创建新的KeyValue对象
    KeyValue oldValue = data.put(key, kv);   // 插入到跳表中，返回旧值
    
    // 只有新键才增加计数（更新操作不增加大小）
    if (oldValue == null) {
        currentSize++;  // 原子性地增加当前大小
    }
}
```

**代码解释**: 写入操作将键值对封装成KeyValue对象后插入跳表。通过检查返回的旧值，我们只对新键增加计数，确保currentSize准确反映MemTable中的唯一键数量。

**性能分析**:
- **时间复杂度**: O(log n)，其中n是MemTable中的键数量
- **并发性能**: ConcurrentSkipListMap支持高并发写入
- **原子性**: 单个put操作是原子的

### 2. 读取操作 (GET)

```java
public String get(String key) {
    KeyValue kv = data.get(key);        // 从跳表中获取KeyValue对象
    if (kv == null || kv.isDeleted()) { // 检查是否存在或已被删除
        return null;                    // 不存在或已删除，返回null
    }
    return kv.getValue();               // 返回实际的值
}
```

**代码解释**: 读取操作首先从跳表中获取KeyValue对象，然后检查该对象是否存在以及是否为删除标记。这种设计统一处理了不存在的键和被逻辑删除的键。

**性能分析**:
- **时间复杂度**: O(log n)
- **并发读取**: 支持多线程并发读取
- **删除处理**: 自动处理墓碑标记

### 3. 删除操作 (DELETE)

```java
public void delete(String key) {
    KeyValue tombstone = KeyValue.createTombstone(key);  // 创建删除标记（墓碑）
    KeyValue oldValue = data.put(key, tombstone);        // 将墓碑插入跳表
    
    // 只有新键才增加计数（即使是删除操作）
    if (oldValue == null) {
        currentSize++;  // 墓碑也占用空间，需要计入大小
    }
}
```

**代码解释**: 删除操作通过插入墓碑标记来实现，而不是物理删除。这保证了删除操作的持久性和可见性。注意即使是删除操作，如果是新键也会增加currentSize，因为墓碑同样占用内存空间。

**删除策略**:
- **逻辑删除**: 插入墓碑标记而非物理删除
- **一致性**: 确保删除操作的可见性
- **空间考虑**: 墓碑占用空间，需要压缩清理

### 4. 刷盘检查

```java
public boolean shouldFlush() {
    return currentSize >= maxSize;  // 当前大小超过阈值时需要刷盘
}

// 获取当前大小的方法
public int size() {
    return currentSize;  // 返回当前的键值对数量
}
```

**代码解释**: 刷盘检查是LSM Tree中的关键操作。当MemTable大小超过阈值时，就需要将数据刷盘到SSTable文件中，为新的写入腾出内存空间。

**刷盘触发**:
- **大小阈值**: 超过maxSize时触发
- **内存压力**: 系统内存不足时强制刷盘
- **时间阈值**: 定期刷盘保证持久性

## 并发控制深入

### ConcurrentSkipListMap 并发机制

```java
// Java ConcurrentSkipListMap 的并发策略示例
public class ConcurrentSkipListMap<K,V> {
    
    // 使用 CAS (Compare-And-Swap) 操作进行无锁更新
    private boolean casNext(Node<K,V> cmp, Node<K,V> val) {
        // 原子性地比较并交换指针，避免使用锁
        return UNSAFE.compareAndSwapObject(this, nextOffset, cmp, val);
    }
    
    // 无锁读取操作
    public V get(Object key) {
        return doGet(key);  // 执行无锁查找
    }
    
    // 无锁写入（大部分情况下）
    public V put(K key, V value) {
        return doPut(key, value, false);  // 执行无锁插入
    }
}
```

**代码解释**: ConcurrentSkipListMap的高并发性能来源于其巧妙的无锁设计。它大量使用CAS操作来实现原子更新，避免了传统锁机制的开销。这使得读操作完全无锁，写操作在大多数情况下也能避免阻塞。


## 小结

MemTable是LSM Tree的核心组件，它通过以下特性实现了高性能：

1. **跳表结构**: 提供O(log n)的操作性能和良好的并发性
2. **并发安全**: ConcurrentSkipListMap确保线程安全
3. **内存效率**: 紧凑的数据结构减少内存开销
4. **有序性**: 支持高效的有序遍历和范围查询

---

## 思考题

1. 为什么ConcurrentSkipListMap比ConcurrentHashMap更适合MemTable？
2. 如何处理MemTable刷盘过程中的新写入？
3. 跳表的随机层数如何影响性能？
