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
Level 3: [1] -----------------> [17] ----------> NULL
Level 2: [1] --------> [9] ----> [17] ----------> NULL
Level 1: [1] -> [4] -> [9] ----> [17] -> [25] -> NULL
Level 0: [1] -> [4] -> [9] -> [12] -> [17] -> [25] -> NULL
           ^
         查找12
```

### 查找过程

1. **从最高层开始**: 从Level 3的头节点开始
2. **水平移动**: 在当前层向右移动，直到下一个节点 > 目标值
3. **向下移动**: 移动到下一层继续查找
4. **重复过程**: 直到找到目标或到达Level 0

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

### 并发性能测试

```java
public class MemTableConcurrencyTest {
    
    public void testConcurrentWrites() throws InterruptedException {
        MemTable memTable = new MemTable(100000);    // 创建大容量MemTable
        int threadCount = 8;                          // 设置并发线程数
        int operationsPerThread = 10000;              // 每线程操作数
        
        CountDownLatch latch = new CountDownLatch(threadCount);  // 同步工具
        long startTime = System.nanoTime();                     // 记录开始时间
        
        // 启动多个写线程
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;  // 为每个线程分配ID
            new Thread(() -> {
                try {
                    // 每个线程执行指定数量的写操作
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "key_" + threadId + "_" + j;          // 生成唯一键
                        String value = "value_" + System.currentTimeMillis();  // 生成值
                        memTable.put(key, value);                         // 执行写入
                    }
                } finally {
                    latch.countDown();  // 完成时通知主线程
                }
            }).start();
        }
        
        latch.await();  // 等待所有线程完成
        long duration = System.nanoTime() - startTime;  // 计算总耗时
        
        int totalOps = threadCount * operationsPerThread;           // 总操作数
        double throughput = totalOps / (duration / 1_000_000_000.0);  // 计算吞吐量
        
        System.out.printf("并发写入测试: %d线程, %d操作, 吞吐量: %.0f ops/sec%n", 
                         threadCount, totalOps, throughput);
    }
}
```

**代码解释**: 这个并发测试验证了MemTable在高并发写入场景下的性能表现。通过多个线程同时写入不同的键，我们可以测量系统的吞吐量和并发能力。使用CountDownLatch确保所有线程同时开始和结束，获得准确的性能数据。

## 内存管理

### 1. 内存占用估算

```java
public class MemTableMemoryAnalysis {
    
    // 估算MemTable的总内存使用量
    public static long estimateMemoryUsage(MemTable memTable) {
        // ConcurrentSkipListMap 的结构开销
        long mapOverhead = estimateSkipListOverhead(memTable.size());
        
        // KeyValue 对象本身的开销
        long keyValueOverhead = memTable.size() * estimateKeyValueSize();
        
        // 字符串数据的实际存储开销
        long stringDataOverhead = estimateStringDataSize(memTable);
        
        return mapOverhead + keyValueOverhead + stringDataOverhead;  // 返回总内存占用
    }
    
    // 估算跳表结构的内存开销
    private static long estimateSkipListOverhead(int size) {
        // 每个节点的平均层数约为 log2(size)
        double avgLevels = Math.log(size) / Math.log(2);
        
        // 每个节点的开销：对象头 + forward数组 + key/value引用
        long nodeOverhead = 12 +                        // 对象头
                           (long)(avgLevels * 8) +      // forward指针数组
                           16;                          // key和value引用
        
        return size * nodeOverhead;  // 总的节点开销
    }
    
    // 估算KeyValue对象的平均大小
    private static long estimateKeyValueSize() {
        return 12 +    // 对象头
               16 +    // 4个字段引用
               8 +     // timestamp字段
               4;      // deleted字段（对齐后）
    }
    
    // 估算字符串数据的存储开销
    private static long estimateStringDataSize(MemTable memTable) {
        // 这里简化处理，实际实现需要遍历所有KeyValue对象
        return memTable.size() * 50;  // 假设平均每个键值对50字节
    }
}
```

**代码解释**: 内存估算对于系统调优至关重要。这个分析包括三个部分：跳表结构开销、KeyValue对象开销和字符串数据开销。通过准确估算内存使用，我们可以合理设置maxSize参数，避免内存溢出。

### 2. 内存优化策略

```java
public class OptimizedMemTable {
    
    // 字符串池化减少重复键的内存占用
    private static final Map<String, String> keyPool = new ConcurrentHashMap<>();
    
    public void put(String key, String value) {
        // 对键进行池化处理，减少重复字符串的内存占用
        String internedKey = keyPool.computeIfAbsent(key, k -> k);
        
        KeyValue kv = new KeyValue(internedKey, value);  // 使用池化的键
        data.put(internedKey, kv);                       // 插入数据
    }
    
    // 定期清理无用的池化键
    public void cleanKeyPool() {
        Set<String> activeKeys = data.keySet();      // 获取当前活跃的键
        keyPool.keySet().retainAll(activeKeys);      // 只保留活跃的键
    }
}
```

**代码解释**: 字符串池化是一种重要的内存优化技术。当系统中存在大量相似的键（如"user:1", "user:2"等）时，池化可以显著减少内存占用。`computeIfAbsent`方法确保线程安全，`cleanKeyPool`方法定期清理不再使用的键。

## 性能优化

### 1. 批量操作

```java
public void putBatch(Map<String, String> entries) {
    // 批量写入减少单个操作的开销
    for (Map.Entry<String, String> entry : entries.entrySet()) {
        String key = entry.getKey();    // 获取键
        String value = entry.getValue(); // 获取值
        put(key, value);                // 执行单个写入操作
    }
    // 注意：这里可以进一步优化，比如批量检查刷盘条件
}
```

**代码解释**: 批量操作通过减少方法调用开销来提高性能。虽然这个实现比较简单，但在实际应用中可以进一步优化，比如批量检查刷盘条件，或者使用更高效的批量插入算法。

### 2. 预分配优化

```java
public MemTable(int maxSize) {
    // 预估初始容量减少扩容开销
    int initialCapacity = Math.min(maxSize / 4, 1000);  // 预估初始容量为最大值的1/4
    this.data = new ConcurrentSkipListMap<>();          // 创建跳表（注：实际JDK实现不支持初始容量）
    this.maxSize = maxSize;                             // 设置最大容量
    this.currentSize = 0;                               // 初始化当前大小
}
```

**代码解释**: 虽然ConcurrentSkipListMap不支持预分配，但这个概念很重要。在其他可以预分配的数据结构中，合理的初始容量可以减少扩容操作的开销，提高整体性能。

### 3. 监控和调优

```java
public class MemTableMetrics {
    private final AtomicLong putCount = new AtomicLong(0);   // 写入操作计数
    private final AtomicLong getCount = new AtomicLong(0);   // 读取操作计数
    private final AtomicLong hitCount = new AtomicLong(0);   // 命中次数计数
    
    // 记录写入操作
    public void recordPut() {
        putCount.incrementAndGet();  // 原子性地增加写入计数
    }
    
    // 记录读取操作及其结果
    public void recordGet(boolean hit) {
        getCount.incrementAndGet();  // 原子性地增加读取计数
        if (hit) {
            hitCount.incrementAndGet();  // 如果命中，增加命中计数
        }
    }
    
    // 计算命中率
    public double getHitRate() {
        long gets = getCount.get();  // 获取总读取次数
        return gets > 0 ? (double) hitCount.get() / gets : 0.0;  // 计算命中率
    }
    
    // 获取所有性能指标
    public String getMetrics() {
        return String.format("PUT: %d, GET: %d, HIT_RATE: %.2f%%", 
                           putCount.get(), getCount.get(), getHitRate() * 100);
    }
}
```

**代码解释**: 性能监控是系统调优的基础。这个指标类使用原子类来确保在高并发环境下计数的准确性。通过跟踪写入次数、读取次数和命中率，我们可以了解MemTable的使用模式和性能表现。

## 实际应用示例

### 1. 高并发写入场景

```java
public class HighThroughputExample {
    
    public static void main(String[] args) {
        MemTable memTable = new MemTable(50000);  // 创建较大容量的MemTable
        
        // 模拟高并发写入场景的参数设置
        int writerThreads = 4;        // 写入线程数
        int writesPerThread = 10000;  // 每个线程的写入次数
        
        ExecutorService executor = Executors.newFixedThreadPool(writerThreads);  // 创建线程池
        CountDownLatch latch = new CountDownLatch(writerThreads);                // 同步计数器
        
        long startTime = System.currentTimeMillis();  // 记录开始时间
        
        // 启动多个写入线程
        for (int i = 0; i < writerThreads; i++) {
            final int threadId = i;  // 为每个线程分配唯一ID
            executor.submit(() -> {
                try {
                    // 每个线程执行大量写入操作
                    for (int j = 0; j < writesPerThread; j++) {
                        String key = "thread_" + threadId + "_key_" + j;           // 生成唯一键
                        String value = "value_" + System.currentTimeMillis();      // 生成时间戳值
                        memTable.put(key, value);                                  // 执行写入
                    }
                } finally {
                    latch.countDown();  // 线程完成时递减计数器
                }
            });
        }
        
        try {
            latch.await();  // 等待所有线程完成
            long duration = System.currentTimeMillis() - startTime;  // 计算总耗时
            int totalWrites = writerThreads * writesPerThread;        // 计算总写入次数
            
            // 输出性能统计信息
            System.out.printf("写入完成: %d条记录, 耗时: %dms, 吞吐量: %.0f ops/sec%n",
                             totalWrites, duration, totalWrites * 1000.0 / duration);
            
            // 输出MemTable状态信息
            System.out.printf("MemTable状态: 大小=%d, 是否需要刷盘=%b%n",
                             memTable.size(), memTable.shouldFlush());
                             
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();  // 恢复中断状态
        } finally {
            executor.shutdown();  // 关闭线程池
        }
    }
}
```

**代码解释**: 这个高吞吐量示例模拟了实际生产环境中的高并发写入场景。通过多个线程并发写入数据，我们可以测试MemTable的并发性能和稳定性。使用线程池管理线程，CountDownLatch确保准确计时。

### 2. 混合读写场景

```java
public class MixedWorkloadExample {
    
    public static void main(String[] args) {
        MemTable memTable = new MemTable(20000);  // 创建MemTable
        
        // 先写入一些基础数据作为读取测试的基础
        for (int i = 0; i < 10000; i++) {
            memTable.put("base_key_" + i, "base_value_" + i);
        }
        
        // 用于统计操作结果的原子计数器
        AtomicInteger reads = new AtomicInteger(0);   // 读取次数
        AtomicInteger writes = new AtomicInteger(0);  // 写入次数
        AtomicInteger hits = new AtomicInteger(0);    // 读取命中次数
        
        // 定义混合读写负载的任务
        Runnable mixedWorkload = () -> {
            Random random = new Random();  // 随机数生成器
            
            // 执行5000次混合操作
            for (int i = 0; i < 5000; i++) {
                if (random.nextDouble() < 0.7) {
                    // 70% 概率执行读操作
                    String key = "base_key_" + random.nextInt(10000);  // 随机选择一个键
                    String value = memTable.get(key);                  // 执行读取
                    reads.incrementAndGet();                           // 增加读取计数
                    if (value != null) {
                        hits.incrementAndGet();  // 如果命中，增加命中计数
                    }
                } else {
                    // 30% 概率执行写操作
                    String key = "new_key_" + random.nextInt(5000);                  // 生成新键
                    String value = "new_value_" + System.currentTimeMillis();        // 生成新值
                    memTable.put(key, value);                                        // 执行写入
                    writes.incrementAndGet();                                        // 增加写入计数
                }
            }
        };
        
        // 启动多个线程执行混合负载
        long startTime = System.currentTimeMillis();  // 记录开始时间
        Thread[] threads = new Thread[4];             // 创建4个线程
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(mixedWorkload);    // 创建执行混合负载的线程
            threads[i].start();                        // 启动线程
        }
        
        // 等待所有线程完成
        for (Thread thread : threads) {
            try {
                thread.join();  // 等待线程结束
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();  // 恢复中断状态
            }
        }
        
        // 计算并输出统计结果
        long duration = System.currentTimeMillis() - startTime;  // 计算总耗时
        int totalOps = reads.get() + writes.get();               // 计算总操作数
        double hitRate = (double) hits.get() / reads.get();      // 计算命中率
        
        System.out.printf("混合负载测试结果:%n");
        System.out.printf("总操作: %d, 读: %d, 写: %d%n", totalOps, reads.get(), writes.get());
        System.out.printf("耗时: %dms, 吞吐量: %.0f ops/sec%n", duration, totalOps * 1000.0 / duration);
        System.out.printf("命中率: %.2f%% (%d/%d)%n", hitRate * 100, hits.get(), reads.get());
    }
}
```

**代码解释**: 这个混合负载示例更贴近实际应用场景，其中读操作占主导地位（70%）。通过预先写入基础数据，我们确保读操作有数据可读。使用原子计数器统计各种操作的次数，最后计算整体性能指标。

## 常见问题和优化

### 1. 内存溢出问题

**问题**: MemTable无限增长导致OOM

**解决方案**:
```java
public class MemTableWithBackpressure {
    private volatile boolean flushInProgress = false;  // 刷盘进行中标志
    
    public void put(String key, String value) {
        // 检查是否需要等待刷盘完成
        while (shouldFlush() && flushInProgress) {
            try {
                Thread.sleep(1);  // 短暂等待，避免CPU空转
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();  // 恢复中断状态
                return;                              // 中断时退出
            }
        }
        
        KeyValue kv = new KeyValue(key, value);  // 创建KeyValue对象
        data.put(key, kv);                       // 插入数据
        currentSize++;                           // 增加大小计数
    }
    
    // 设置刷盘状态的方法
    public void setFlushInProgress(boolean inProgress) {
        this.flushInProgress = inProgress;  // 更新刷盘状态
    }
}
```

**代码解释**: 背压控制机制防止MemTable无限增长。当MemTable需要刷盘且正在进行刷盘操作时，新的写入会暂时等待。这种简单的流控机制可以防止内存溢出，但需要注意不要造成长时间的阻塞。

### 2. 热点键问题

**问题**: 某些键被频繁访问导致竞争

**优化**: 
```java
// 使用分段策略减少热点竞争
public class ShardedMemTable {
    private final MemTable[] shards;        // MemTable分片数组
    private final int shardMask;            // 分片掩码，用于快速计算分片索引
    
    public ShardedMemTable(int shardCount, int maxSizePerShard) {
        this.shards = new MemTable[shardCount];           // 创建分片数组
        this.shardMask = shardCount - 1;                  // 计算掩码（假设shardCount是2的幂）
        
        // 初始化每个分片
        for (int i = 0; i < shardCount; i++) {
            shards[i] = new MemTable(maxSizePerShard);    // 创建分片MemTable
        }
    }
    
    // 根据键的哈希值选择对应的分片
    private MemTable getShard(String key) {
        int hash = key.hashCode();           // 计算键的哈希值
        int shardIndex = hash & shardMask;   // 使用位运算快速计算分片索引
        return shards[shardIndex];           // 返回对应的分片
    }
    
    // 分片写入操作
    public void put(String key, String value) {
        getShard(key).put(key, value);       // 将操作路由到对应分片
    }
    
    // 分片读取操作
    public String get(String key) {
        return getShard(key).get(key);       // 从对应分片读取数据
    }
    
    // 检查是否有分片需要刷盘
    public boolean shouldFlush() {
        for (MemTable shard : shards) {
            if (shard.shouldFlush()) {
                return true;                 // 任何分片需要刷盘都返回true
            }
        }
        return false;
    }
}
```

**代码解释**: 分片策略通过将数据分散到多个MemTable中来减少热点竞争。每个键根据其哈希值被路由到特定的分片，这样不同的键可以并行访问不同的分片。使用位运算（&操作）来快速计算分片索引，提高路由效率。

## 小结

MemTable是LSM Tree的核心组件，它通过以下特性实现了高性能：

1. **跳表结构**: 提供O(log n)的操作性能和良好的并发性
2. **并发安全**: ConcurrentSkipListMap确保线程安全
3. **内存效率**: 紧凑的数据结构减少内存开销
4. **有序性**: 支持高效的有序遍历和范围查询

## 下一步学习

现在你已经理解了MemTable的实现，接下来我们将学习数据如何从内存持久化到磁盘：

继续阅读：[第4章：SSTable 磁盘存储](04-sstable-disk-storage.md)

---

## 思考题

1. 为什么ConcurrentSkipListMap比ConcurrentHashMap更适合MemTable？
2. 如何处理MemTable刷盘过程中的新写入？
3. 跳表的随机层数如何影响性能？

**下一章预告**: 我们将深入学习SSTable的文件格式、索引结构和查询优化。 
**下一章预告**: 我们将深入学习SSTable的文件格式、索引结构和查询优化。 