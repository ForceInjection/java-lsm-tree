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

# 第7章：压缩策略

## 什么是压缩？

**压缩 (Compaction)** 是LSM Tree的核心机制，负责合并多个SSTable文件以：

- **减少文件数量**: 避免查询时需要检查太多文件
- **清理冗余数据**: 删除过期版本和墓碑标记
- **优化空间利用**: 提高存储效率
- **维护有序性**: 保持数据在磁盘上的有序排列

## 压缩触发条件

```
压缩触发场景:
1. SSTable文件数量超过阈值
2. 某层文件大小超过限制
3. 手动触发压缩
4. 定期压缩任务
5. 读放大过大时
```

## 压缩策略类型

### 1. Size-Tiered 压缩策略

我们采用**Size-Tiered压缩**策略，它是一种基于文件数量触发的分层压缩机制：

**核心思想**：
- 将SSTable文件按层级(Level)组织
- 每层允许的最大文件数量有限制
- 当某层文件数量达到阈值时，将该层所有文件合并到下一层
- 层级越高，文件越大，数量越少

**具体工作流程**：

| 层级 | 最大文件数 | 单文件大小 | 总容量 | 触发条件 |
|------|------------|------------|--------|----------|
| Level 0 | 4个文件 | ~10MB | ~40MB | 4个文件时触发 |
| Level 1 | 4个文件 | ~40MB | ~160MB | 4个文件时触发 |
| Level 2 | 4个文件 | ~160MB | ~640MB | 4个文件时触发 |
| Level N | 4个文件 | 4^N × 10MB | 递增 | 4个文件时触发 |

**压缩触发示例**：
```
步骤1: MemTable刷盘产生SSTable文件
Level 0: [file1.sst] [file2.sst] [file3.sst] [file4.sst] ← 达到4个文件

步骤2: Level 0达到阈值，触发压缩到Level 1  
Level 0: [] (清空)
Level 1: [merged_L1_1.sst] ← 4个文件合并成1个大文件

步骤3: 继续写入，Level 0再次累积
Level 0: [file5.sst] [file6.sst] [file7.sst] [file8.sst] ← 又达到4个
Level 1: [merged_L1_1.sst] [merged_L1_2.sst] ← 新压缩的文件

步骤4: Level 1达到阈值，触发向Level 2压缩
Level 1: [] (清空)
Level 2: [merged_L2_1.sst] ← 更大的合并文件
```

**优势分析**：
- ✅ **写入友好**: Level 0直接接收MemTable，写入延迟低
- ✅ **空间效率**: 定期清理过期数据和删除标记
- ✅ **读取优化**: 减少需要查询的文件数量
- ⚠️ **写放大**: 数据可能被多次压缩（但比Leveled更少）

## 压缩策略实现

### 核心实现

```java
package com.brianxiadong.lsmtree;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CompactionStrategy {
    private final String dataDirectory;              // 数据存储目录路径
    private final int maxFilesPerLevel;              // 每层允许的最大文件数量
    private final Map<Integer, List<String>> levelFiles; // 层级到文件列表的映射
    
    // 压缩策略构造器
    public CompactionStrategy(String dataDirectory, int maxFilesPerLevel) {
        this.dataDirectory = dataDirectory;          // 设置数据目录
        this.maxFilesPerLevel = maxFilesPerLevel;    // 设置每层文件数量阈值
        // 使用ConcurrentHashMap确保多线程安全访问层级文件映射
        this.levelFiles = new ConcurrentHashMap<>();
    }
    
    // 将新的SSTable文件添加到Level 0（MemTable刷盘后调用）
    public void addSSTable(String filePath) {
        // computeIfAbsent确保Level 0的文件列表存在，然后添加新文件
        levelFiles.computeIfAbsent(0, k -> new ArrayList<>()).add(filePath);
    }
    
    // 检查指定层级是否需要压缩
    public boolean needsCompaction(int level) {
        List<String> files = levelFiles.get(level);  // 获取该层的文件列表
        // 当文件数量达到或超过阈值时需要压缩
        return files != null && files.size() >= maxFilesPerLevel;
    }
    
    // 执行指定层级的压缩操作
    public void compact(int level) throws IOException {
        if (!needsCompaction(level)) {               // 检查是否真的需要压缩
            return;                                  // 不需要压缩，直接返回
        }
        
        // 获取当前层所有需要压缩的文件（创建副本避免并发修改）
        List<String> filesToCompact = new ArrayList<>(levelFiles.get(level));
        // 执行实际的文件合并操作，生成下一层的文件
        String compactedFile = performCompaction(filesToCompact, level + 1);
        
        // 更新文件层级结构
        levelFiles.get(level).clear();              // 清空当前层（文件已合并）
        // 将合并后的文件添加到下一层
        levelFiles.computeIfAbsent(level + 1, k -> new ArrayList<>()).add(compactedFile);
        
        // 物理删除旧的SSTable文件，释放磁盘空间
        cleanupOldFiles(filesToCompact);
        
        // 递归检查下一层是否也需要压缩（压缩可能引发连锁反应）
        if (needsCompaction(level + 1)) {
            compact(level + 1);                     // 递归压缩下一层
        }
    }
}
```

**核心设计解析**：这个压缩策略管理器是Size-Tiered压缩的控制中心。它维护了一个层级到文件列表的映射，使用`ConcurrentHashMap`确保多线程安全。`addSSTable`方法将新文件添加到Level 0，这是MemTable刷盘的入口点。`needsCompaction`方法简单但关键，它决定了压缩的触发时机。`compact`方法实现了压缩的完整流程：检查→合并→更新→清理→递归检查，这种设计确保了压缩操作的原子性和连锁效应的正确处理。

### 压缩执行器

```java
// 执行实际的文件压缩合并操作
private String performCompaction(List<String> inputFiles, int targetLevel) throws IOException {
    // 打印压缩开始信息，用于监控和调试
    System.out.printf("开始压缩 %d 个文件到 Level %d%n", inputFiles.size(), targetLevel);
    
    // 步骤1: 加载所有需要压缩的输入SSTable文件
    List<SSTable> inputTables = new ArrayList<>();
    for (String filePath : inputFiles) {
        // 从磁盘文件加载SSTable对象到内存
        inputTables.add(SSTable.loadFromFile(filePath));  
    }
    
    // 步骤2: 使用多路归并算法合并所有SSTable的数据
    List<KeyValue> mergedData = mergeSSTableData(inputTables);
    
    // 步骤3: 数据去重和清理（移除过期版本、处理删除标记）
    List<KeyValue> cleanedData = deduplicateAndClean(mergedData);
    
    // 步骤4: 为目标层级生成新的SSTable文件名
    String outputFile = generateCompactedFileName(targetLevel);
    
    // 步骤5: 将清理后的数据写入新的SSTable文件
    SSTable compactedTable = new SSTable(outputFile, cleanedData);
    
    // 打印压缩完成信息，显示压缩效果统计
    System.out.printf("压缩完成: %s (清理前: %d条, 清理后: %d条)%n", 
                     outputFile, mergedData.size(), cleanedData.size());
    
    return outputFile;  // 返回新生成的SSTable文件路径
}
```

**压缩执行解析**：这个方法是压缩操作的核心执行引擎，它将多个SSTable文件合并成一个更大的文件。整个过程分为5个清晰的步骤：文件加载、数据合并、数据清理、文件命名、文件写入。数据合并使用多路归并算法确保输出数据的有序性，数据清理阶段移除冗余版本和处理删除操作，这是压缩提高存储效率的关键环节。统计信息的输出帮助监控压缩效果，了解数据清理的程度。

### 数据合并算法

```java
// 使用多路归并算法合并多个有序SSTable的数据
private List<KeyValue> mergeSSTableData(List<SSTable> tables) {
    // 创建最小堆，用于多路归并排序（按key字典序排序）
    PriorityQueue<SSTableIterator> heap = new PriorityQueue<>(
        Comparator.comparing(iter -> iter.current().getKey())  // 比较器：按当前key排序
    );
    
    // 初始化所有SSTable的迭代器，将第一个元素放入堆
    for (SSTable table : tables) {
        SSTableIterator iter = table.iterator();    // 获取表的迭代器
        if (iter.hasNext()) {                      // 如果表非空
            iter.next();                           // 移动到第一个元素
            heap.offer(iter);                      // 将迭代器放入堆
        }
    }
    
    List<KeyValue> merged = new ArrayList<>();     // 存储合并后的有序数据
    
    // 执行多路归并：每次取出最小key的迭代器
    while (!heap.isEmpty()) {
        SSTableIterator iter = heap.poll();        // 取出堆顶（最小key）的迭代器
        KeyValue current = iter.current();         // 获取当前最小的KeyValue
        merged.add(current);                       // 添加到结果中
        
        // 如果该迭代器还有数据，继续放入堆中
        if (iter.hasNext()) {
            iter.next();                           // 移动到下一个元素
            heap.offer(iter);                      // 重新放入堆中排序
        }
    }
    
    return merged;  // 返回合并排序后的所有数据
}

// SSTable迭代器：用于遍历单个SSTable的数据
private static class SSTableIterator {
    private final List<KeyValue> data;    // SSTable中的所有数据
    private int index = -1;               // 当前位置索引（-1表示未开始）
    
    // 构造器：从SSTable获取所有数据
    public SSTableIterator(SSTable table) {
        this.data = table.getAllData();   // 加载SSTable的所有KeyValue数据
    }
    
    // 检查是否还有下一个元素
    public boolean hasNext() {
        return index + 1 < data.size();   // 判断下一个位置是否有效
    }
    
    // 移动到下一个元素并返回
    public KeyValue next() {
        if (!hasNext()) {                 // 边界检查
            throw new NoSuchElementException("没有更多元素");
        }
        return data.get(++index);         // 先移动索引，再返回元素
    }
    
    // 获取当前元素（不移动索引）
    public KeyValue current() {
        if (index < 0 || index >= data.size()) {  // 检查索引有效性
            return null;                           // 无效位置返回null
        }
        return data.get(index);                    // 返回当前位置的元素
    }
}
```

**多路归并解析**：这是压缩算法的核心，它将多个有序的SSTable合并成一个大的有序序列。算法使用最小堆维护各个SSTable的"当前最小元素"，每次取出全局最小的key，确保输出序列的有序性。时间复杂度为O(N log K)，其中N是总元素数，K是SSTable数量。这种设计既保证了合并效率，又维持了LSM Tree要求的数据有序性。迭代器模式使得内存使用可控，即使处理大文件也不会出现内存溢出。

### 去重和清理

```java
// 对已排序的数据进行去重和清理，移除过期版本和删除标记
private List<KeyValue> deduplicateAndClean(List<KeyValue> sortedData) {
    if (sortedData.isEmpty()) {               // 空数据直接返回
        return sortedData;
    }
    
    List<KeyValue> cleaned = new ArrayList<>();  // 存储清理后的数据
    String lastKey = null;                       // 上一个处理的key
    KeyValue lastKV = null;                      // 上一个key的最新版本
    
    // 遍历所有已排序的KeyValue（按key排序，相同key按timestamp排序）
    for (KeyValue kv : sortedData) {
        String currentKey = kv.getKey();         // 获取当前记录的key
        
        if (!currentKey.equals(lastKey)) {       // 遇到新的key
            // 处理上一个key：添加其最新版本（如果未被删除）
            if (lastKV != null && !lastKV.isDeleted()) {
                cleaned.add(lastKV);             // 只保留未删除的记录
            }
            // 开始处理新key
            lastKey = currentKey;                // 更新当前处理的key
            lastKV = kv;                        // 设置当前版本为候选最新版本
        } else {
            // 相同key的多个版本：保留时间戳最新的版本
            if (kv.getTimestamp() > lastKV.getTimestamp()) {
                lastKV = kv;                    // 更新为更新的版本
            }
            // 旧版本被丢弃，实现去重
        }
    }
    
    // 处理最后一个key：添加其最新版本（如果未被删除）
    if (lastKV != null && !lastKV.isDeleted()) {
        cleaned.add(lastKV);                    // 确保最后一个key也被处理
    }
    
    return cleaned;  // 返回去重和清理后的数据
}
```

**去重清理解析**：这是压缩过程中数据优化的关键步骤，它实现了LSM Tree的两个重要功能：版本去重和删除处理。对于同一个key的多个版本，只保留时间戳最新的版本，这大大减少了存储空间。对于标记为删除的记录，在压缩时彻底移除，释放存储空间。算法的时间复杂度为O(N)，空间复杂度也是O(N)，效率很高。这种设计确保了压缩后的数据既保持了最新状态，又最大化了存储效率。



## 小结

压缩策略是LSM Tree性能的关键：

1. **文件管理**: 控制文件数量和大小
2. **空间回收**: 清理冗余和删除的数据
3. **性能平衡**: 在读写性能间找到平衡
4. **自适应**: 根据负载模式调整策略

---

## 思考题

1. 为什么需要多层压缩？
2. 如何选择合适的压缩触发条件？
3. 压缩过程中如何保证数据一致性？

**下一章预告**: 我们将学习如何将所有组件整合成完整的LSM Tree系统。 