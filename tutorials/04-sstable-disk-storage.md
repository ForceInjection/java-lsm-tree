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

# 第4章：SSTable 磁盘存储

## 什么是SSTable？

**SSTable (Sorted String Table)** 是LSM Tree中存储在磁盘上的不可变有序文件。当MemTable达到大小阈值时，会将其内容刷盘生成SSTable文件。

## SSTable 核心特性

### 1. 不可变性 (Immutability)
- 一旦写入完成，SSTable文件永不修改
- 更新操作通过新的SSTable体现
- 删除操作通过墓碑标记实现

### 2. 有序性 (Sorted)
- 所有键值对key的字典序排列
- 支持高效的二分查找
- 便于合并操作

### 3. 自包含性 (Self-contained)
- 包含布隆过滤器用于快速过滤
- 包含索引信息加速查找
- 包含元数据信息

> 关于布隆过滤器，大家先简单认为用来判断数据存在不存在即可，下一小节会详细进行讲解。

## 文件格式设计

我们的SSTable采用简化的文件格式：

```
┌─────────────────────────────────────────────────────────────┐
│                    SSTable 文件结构                         │
├─────────────────────────────────────────────────────────────┤
│  [条目数量: 4字节]                                           │
├─────────────────────────────────────────────────────────────┤
│  [数据条目区域]                                             │
│    条目1: key|value|timestamp|deleted                       │
│    条目2: key|value|timestamp|deleted                       │
│    ...                                                      │
│    条目N: key|value|timestamp|deleted                       │
├─────────────────────────────────────────────────────────────┤
│  [布隆过滤器区域]                                           │
│    过滤器数据                                               │
└─────────────────────────────────────────────────────────────┘
```

## SSTable 实现解析

让我们深入分析SSTable的实现：

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
    // 文件路径：SSTable存储在磁盘上的位置
    private final String filePath;
    // 布隆过滤器：用于快速判断键是否可能存在
    private final BloomFilter bloomFilter;
    // 创建时间：用于压缩时的文件排序
    private final long creationTime;
    
    // 构造函数：从有序数据创建SSTable
    public SSTable(String filePath, List<KeyValue> sortedData) throws IOException {
        this.filePath = filePath;                           // 设置文件路径
        this.creationTime = System.currentTimeMillis();    // 记录创建时间
        // 创建布隆过滤器，估算条目数和假阳性率
        this.bloomFilter = new BloomFilter(sortedData.size(), 0.01);
        
        // 将数据持久化到磁盘文件
        writeToFile(sortedData);
    }
    
    /**
     * 从文件路径加载已存在的SSTable
     */
    public SSTable(String filePath) throws IOException {
        this.filePath = filePath;                                           // 设置文件路径
        this.creationTime = Files.getLastModifiedTime(Paths.get(filePath)) // 获取文件修改时间作为创建时间
                .toMillis();
        this.bloomFilter = new BloomFilter(1000, 0.01);                    // 创建临时布隆过滤器
        
        // 重新构建布隆过滤器
        rebuildBloomFilter();
    }
}
```

**代码解释**: 这个SSTable类是LSM Tree持久化存储的核心。它包含三个关键组件：文件路径用于磁盘存储，布隆过滤器用于快速过滤不存在的键，有序数据列表用于内存中的快速访问。构造函数会自动创建布隆过滤器并将数据写入磁盘。

### 核心方法分析

#### 1. 写入文件 (writeToFile)

```java
/**
 * 将排序数据写入文件
 */
private void writeToFile(List<KeyValue> sortedData) throws IOException {
    // 使用DataOutputStream进行二进制写入，性能更好
    try (DataOutputStream dos = new DataOutputStream(
            new BufferedOutputStream(new FileOutputStream(filePath)))) {

        // 写入条目数量
        dos.writeInt(sortedData.size());                    // 写入整数类型的条目数

        // 写入所有数据条目
        for (KeyValue kv : sortedData) {
            // 添加到布隆过滤器
            bloomFilter.add(kv.getKey());                   // 添加键到过滤器

            // 写入数据：key, deleted, value(如果不是删除), timestamp
            dos.writeUTF(kv.getKey());                      // 写入键（UTF-8编码）
            dos.writeBoolean(kv.isDeleted());               // 写入删除标记
            if (!kv.isDeleted()) {                          // 如果不是删除操作
                dos.writeUTF(kv.getValue());                // 写入值
            }
            dos.writeLong(kv.getTimestamp());               // 写入时间戳
        }
    }
    // try-with-resources自动关闭DataOutputStream
}
```

**代码解释**: 写入过程采用顺序写入策略，这是磁盘I/O的最优模式。文件格式简单明了：首先是条目数量，然后是所有数据条目，最后是布隆过滤器。使用管道符（|）分隔字段，便于解析。BufferedWriter提供缓冲以提高写入性能。

**写入过程分析**:
1. **顺序写入**: 充分利用磁盘顺序写入的高性能
2. **文本格式**: 便于调试和人工检查
3. **完整性**: 包含所有必要的元数据

#### 2. 重建布隆过滤器 (rebuildBloomFilter)

```java
/**
 * 重新构建布隆过滤器
 */
private void rebuildBloomFilter() throws IOException {
    // 使用DataInputStream读取二进制文件
    try (DataInputStream dis = new DataInputStream(
            new BufferedInputStream(new FileInputStream(filePath)))) {

        int totalEntries = dis.readInt();                   // 读取条目总数

        // 遍历所有条目，将键添加到布隆过滤器
        for (int i = 0; i < totalEntries; i++) {
            String key = dis.readUTF();                     // 读取键
            boolean deleted = dis.readBoolean();            // 读取删除标记
            if (!deleted) {                                 // 如果不是删除操作
                dis.readUTF();                              // 跳过value，只读取键用于布隆过滤器
            }
            dis.readLong();                                 // 跳过timestamp

            // 添加到布隆过滤器
            bloomFilter.add(key);                           // 将键添加到过滤器
        }
    }
    // try-with-resources自动关闭DataInputStream
}
```

**代码解释**: 加载过程是写入的逆向操作。先读取条目数量以便预估内存需求，然后逐行解析数据条目，最后恢复布隆过滤器。如果布隆过滤器数据损坏，会自动重建，增强了系统的容错性。解析使用split方法按管道符分割字段。

#### 3. 查询操作 (get)

```java
/**
 * 查询键值 - 简化实现，顺序搜索
 */
public String get(String key) {
    // 首先检查布隆过滤器
    if (!bloomFilter.mightContain(key)) {
        return null;                                        // 布隆过滤器说不存在，直接返回null
    }

    // 布隆过滤器说可能存在，读取文件进行查找
    try (DataInputStream dis = new DataInputStream(
            new BufferedInputStream(new FileInputStream(filePath)))) {

        int totalEntries = dis.readInt();                   // 读取条目总数

        // 顺序搜索所有条目
        for (int i = 0; i < totalEntries; i++) {
            String currentKey = dis.readUTF();              // 读取当前键
            boolean deleted = dis.readBoolean();            // 读取删除标记
            String value = null;                            // 初始化值
            if (!deleted) {                                 // 如果不是删除操作
                value = dis.readUTF();                      // 读取值
            }
            long timestamp = dis.readLong();                // 读取时间戳

            // 检查是否找到目标键
            if (currentKey.equals(key)) {
                return deleted ? null : value;              // 如果是删除标记返回null，否则返回值
            }

            // 由于数据有序，如果当前键大于目标键，则不存在
            if (currentKey.compareTo(key) > 0) {
                break;                                      // 提前退出循环
            }
        }
    } catch (IOException e) {
        e.printStackTrace();                                // 打印异常信息
    }

    return null;                                            // 未找到，返回null
}
```

**代码解释**: 查询操作采用两阶段策略：首先用布隆过滤器快速过滤不存在的键，这能避免大部分无效的磁盘访问。如果布隆过滤器表示键可能存在，再进行文件扫描。由于数据有序存储，当遇到比目标键大的键时可以提前退出。这种实现虽然是顺序查找，但在实际应用中由于布隆过滤器的过滤效果，大多数查询都不需要访问磁盘。

#### 4. 获取所有条目 (getAllEntries)

```java
/**
 * 获取所有键值对（用于合并）
 */
public List<KeyValue> getAllEntries() throws IOException {
    List<KeyValue> entries = new ArrayList<>();            // 创建结果列表

    // 读取文件中的所有数据
    try (DataInputStream dis = new DataInputStream(
            new BufferedInputStream(new FileInputStream(filePath)))) {

        int totalEntries = dis.readInt();                   // 读取条目总数

        // 读取所有条目
        for (int i = 0; i < totalEntries; i++) {
            String key = dis.readUTF();                     // 读取键
            boolean deleted = dis.readBoolean();            // 读取删除标记
            String value = null;                            // 初始化值
            if (!deleted) {                                 // 如果不是删除操作
                value = dis.readUTF();                      // 读取值
            }
            long timestamp = dis.readLong();                // 读取时间戳

            // 创建KeyValue对象并添加到列表
            entries.add(new KeyValue(key, value, timestamp, deleted));
        }
    }

    return entries;                                         // 返回所有条目
}

/**
 * 删除SSTable文件
 */
public void delete() throws IOException {
    Files.deleteIfExists(Paths.get(filePath));              // 删除文件，如果存在的话
}

// Getter方法
public String getFilePath() {
    return filePath;                                        // 返回文件路径
}

public long getCreationTime() {
    return creationTime;                                    // 返回创建时间
}
```

**代码解释**: `getAllEntries`方法用于压缩过程中读取SSTable的所有数据。它按顺序读取文件中的所有条目，重建KeyValue对象。`delete`方法提供文件清理功能，在压缩完成后删除旧的SSTable文件。Getter方法提供对文件路径和创建时间的访问，用于压缩策略中的文件排序。


## 小结

SSTable是LSM Tree的持久化存储层，具有以下关键特性：

1. **不可变性**: 确保数据一致性和线程安全
2. **有序性**: 支持高效查找和范围查询
3. **自包含**: 包含布隆过滤器和索引信息
4. **优化**: 多种性能优化技术

---

## 思考题

1. 为什么SSTable要设计为不可变的？
2. 布隆过滤器如何提升SSTable的查询性能？
3. 如何在保持性能的同时减少SSTable的磁盘占用？
