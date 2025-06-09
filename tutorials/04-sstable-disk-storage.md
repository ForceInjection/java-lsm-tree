# 第4章：SSTable 磁盘存储

## 什么是SSTable？

**SSTable (Sorted String Table)** 是LSM Tree中存储在磁盘上的不可变有序文件。当MemTable达到大小阈值时，会将其内容刷盘生成SSTable文件。

## SSTable 核心特性

### 1. 不可变性 (Immutability)
- 一旦写入完成，SSTable文件永不修改
- 更新操作通过新的SSTable体现
- 删除操作通过墓碑标记实现

### 2. 有序性 (Sorted)
- 所有键值对按键的字典序排列
- 支持高效的二分查找
- 便于合并操作

### 3. 自包含性 (Self-contained)
- 包含布隆过滤器用于快速过滤
- 包含索引信息加速查找
- 包含元数据信息

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
import java.util.*;

public class SSTable {
    // 文件路径：SSTable存储在磁盘上的位置
    private final String filePath;
    // 布隆过滤器：用于快速判断键是否可能存在
    private final BloomFilter bloomFilter;
    // 有序数据：内存中的键值对，按键排序
    private final List<KeyValue> sortedData;
    
    // 构造函数：从有序数据创建SSTable
    public SSTable(String filePath, List<KeyValue> sortedData) throws IOException {
        this.filePath = filePath;                           // 设置文件路径
        this.sortedData = new ArrayList<>(sortedData);      // 复制数据到内部列表
        
        // 创建布隆过滤器，估算条目数和假阳性率
        this.bloomFilter = new BloomFilter(sortedData.size(), 0.01);
        // 将所有键添加到布隆过滤器中
        for (KeyValue kv : sortedData) {
            bloomFilter.add(kv.getKey());                   // 添加键到过滤器
        }
        
        // 将数据持久化到磁盘文件
        writeToFile();
    }
    
    // 静态工厂方法：从文件加载SSTable
    public static SSTable loadFromFile(String filePath) throws IOException {
        return new SSTable(filePath);                       // 调用私有构造函数加载
    }
}
```

**代码解释**: 这个SSTable类是LSM Tree持久化存储的核心。它包含三个关键组件：文件路径用于磁盘存储，布隆过滤器用于快速过滤不存在的键，有序数据列表用于内存中的快速访问。构造函数会自动创建布隆过滤器并将数据写入磁盘。

### 核心方法分析

#### 1. 写入文件 (writeToFile)

```java
private void writeToFile() throws IOException {
    // 使用UTF-8编码创建缓冲写入器，自动资源管理
    try (BufferedWriter writer = new BufferedWriter(
            new FileWriter(filePath, StandardCharsets.UTF_8))) {
        
        // 首先写入条目数量，便于读取时预分配空间
        writer.write(String.valueOf(sortedData.size()));
        writer.newLine();                                   // 添加换行符
        
        // 写入所有数据条目，每行一个键值对
        for (KeyValue kv : sortedData) {
            // 格式化键值对为管道分隔的字符串
            String line = String.format("%s|%s|%d|%b",
                    kv.getKey(),                            // 键
                    kv.getValue() != null ? kv.getValue() : "",  // 值（null时用空字符串）
                    kv.getTimestamp(),                      // 时间戳
                    kv.isDeleted());                        // 删除标记
            writer.write(line);                             // 写入格式化的行
            writer.newLine();                               // 添加换行符
        }
        
        // 最后写入序列化的布隆过滤器数据
        String filterData = bloomFilter.serialize();        // 序列化布隆过滤器
        writer.write(filterData);                           // 写入过滤器数据
        writer.newLine();                                   // 添加换行符
    }
    // try-with-resources自动关闭writer
}
```

**代码解释**: 写入过程采用顺序写入策略，这是磁盘I/O的最优模式。文件格式简单明了：首先是条目数量，然后是所有数据条目，最后是布隆过滤器。使用管道符（|）分隔字段，便于解析。BufferedWriter提供缓冲以提高写入性能。

**写入过程分析**:
1. **顺序写入**: 充分利用磁盘顺序写入的高性能
2. **文本格式**: 便于调试和人工检查
3. **完整性**: 包含所有必要的元数据

#### 2. 从文件加载 (loadFromFile)

```java
// 私有构造函数：从文件加载SSTable
private SSTable(String filePath) throws IOException {
    this.filePath = filePath;                               // 设置文件路径
    this.sortedData = new ArrayList<>();                    // 初始化数据列表
    
    // 使用UTF-8编码创建缓冲读取器
    try (BufferedReader reader = new BufferedReader(
            new FileReader(filePath, StandardCharsets.UTF_8))) {
        
        // 读取第一行获取条目数量
        String countLine = reader.readLine();
        int entryCount = Integer.parseInt(countLine);       // 解析条目数量
        
        // 根据条目数量读取所有数据条目
        for (int i = 0; i < entryCount; i++) {
            String line = reader.readLine();                // 读取一行数据
            KeyValue kv = parseKeyValue(line);              // 解析为KeyValue对象
            sortedData.add(kv);                             // 添加到数据列表
        }
        
        // 读取最后一行重建布隆过滤器
        String filterLine = reader.readLine();
        this.bloomFilter = BloomFilter.deserialize(filterLine);  // 反序列化布隆过滤器
        
        // 如果布隆过滤器反序列化失败，重建它
        if (this.bloomFilter == null) {
            rebuildBloomFilter();                           // 重建布隆过滤器
        }
    }
    // try-with-resources自动关闭reader
}

// 解析单行数据为KeyValue对象
private KeyValue parseKeyValue(String line) {
    String[] parts = line.split("\\|");                    // 按管道符分割
    String key = parts[0];                                  // 解析键
    String value = parts[1].isEmpty() ? null : parts[1];   // 解析值（空字符串转null）
    long timestamp = Long.parseLong(parts[2]);              // 解析时间戳
    boolean deleted = Boolean.parseBoolean(parts[3]);       // 解析删除标记
    
    // 使用解析的数据创建KeyValue对象
    return new KeyValue(key, value, timestamp, deleted);
}

// 重建布隆过滤器（当反序列化失败时）
private void rebuildBloomFilter() {
    // 根据数据大小创建新的布隆过滤器
    this.bloomFilter = new BloomFilter(sortedData.size(), 0.01);
    // 将所有键重新添加到过滤器中
    for (KeyValue kv : sortedData) {
        bloomFilter.add(kv.getKey());                       // 添加键到过滤器
    }
}
```

**代码解释**: 加载过程是写入的逆向操作。先读取条目数量以便预估内存需求，然后逐行解析数据条目，最后恢复布隆过滤器。如果布隆过滤器数据损坏，会自动重建，增强了系统的容错性。解析使用split方法按管道符分割字段。

#### 3. 查询操作 (get)

```java
public String get(String key) {
    // 第一步：使用布隆过滤器快速检查键是否可能存在
    if (!bloomFilter.mightContain(key)) {
        return null;                                        // 布隆过滤器说不存在，直接返回null
    }
    
    // 第二步：布隆过滤器说可能存在，进行二分查找
    int index = binarySearch(key);                          // 二分查找键的索引
    if (index >= 0) {                                       // 找到了键
        KeyValue kv = sortedData.get(index);                // 获取对应的KeyValue
        if (kv.isDeleted()) {                               // 检查是否为删除标记
            return null;                                    // 已删除，返回null
        }
        return kv.getValue();                               // 返回实际的值
    }
    
    return null;                                            // 未找到，返回null
}

// 二分查找实现
private int binarySearch(String key) {
    int left = 0;                                           // 左边界
    int right = sortedData.size() - 1;                      // 右边界
    
    // 标准二分查找循环
    while (left <= right) {
        int mid = (left + right) / 2;                       // 计算中点
        String midKey = sortedData.get(mid).getKey();       // 获取中点的键
        int cmp = midKey.compareTo(key);                    // 比较键值
        
        if (cmp == 0) {                                     // 找到目标键
            return mid;                                     // 返回索引
        } else if (cmp < 0) {                              // 中点键小于目标键
            left = mid + 1;                                 // 搜索右半部分
        } else {                                           // 中点键大于目标键
            right = mid - 1;                                // 搜索左半部分
        }
    }
    
    return -1;                                              // 未找到，返回-1
}
```

**代码解释**: 查询操作采用两阶段策略：首先用布隆过滤器快速过滤不存在的键，这能避免大部分无效的磁盘访问。如果布隆过滤器表示键可能存在，再用二分查找精确定位。二分查找利用了数据的有序性，时间复杂度为O(log n)。最后还要检查删除标记，确保不返回已删除的数据。

## 性能优化技术

### 1. 布隆过滤器优化

```java
public class BloomFilterOptimization {
    
    // 根据数据量动态调整布隆过滤器参数
    public static BloomFilter createOptimalFilter(int expectedEntries) {
        double optimalFpp;  // 最优假阳性率
        
        // 根据数据量选择不同的假阳性率
        if (expectedEntries < 1000) {
            optimalFpp = 0.001;                             // 小数据集用更低的假阳性率
        } else if (expectedEntries < 10000) {
            optimalFpp = 0.01;                              // 中等数据集用标准假阳性率
        } else {
            optimalFpp = 0.05;                              // 大数据集平衡内存和性能
        }
        
        return new BloomFilter(expectedEntries, optimalFpp); // 创建优化的布隆过滤器
    }
    
    // 多级布隆过滤器：提供更精确的过滤
    public static class HierarchicalBloomFilter {
        private final BloomFilter coarseFilter;             // 粗粒度过滤器（低假阳性率）
        private final BloomFilter fineFilter;               // 细粒度过滤器（高假阳性率）
        
        public HierarchicalBloomFilter(int expectedEntries) {
            // 粗过滤器用较高假阳性率但更少内存
            this.coarseFilter = new BloomFilter(expectedEntries, 0.1);
            // 细过滤器用较低假阳性率但更多内存
            this.fineFilter = new BloomFilter(expectedEntries, 0.01);
        }
        
        // 添加键到两个过滤器
        public void add(String key) {
            coarseFilter.add(key);                           // 添加到粗过滤器
            fineFilter.add(key);                             // 添加到细过滤器
        }
        
        // 两级检查：先粗过滤再细过滤
        public boolean mightContain(String key) {
            return coarseFilter.mightContain(key) &&         // 粗过滤器检查
                   fineFilter.mightContain(key);             // 细过滤器检查
        }
    }
}
```

**代码解释**: 布隆过滤器优化包括两个策略：动态参数调整和多级过滤。动态调整根据数据量选择合适的假阳性率，小数据集可以承受更低的假阳性率。多级过滤使用两个布隆过滤器，先用内存较少的粗过滤器快速过滤，再用更精确的细过滤器进一步过滤，在内存使用和过滤精度之间找到平衡。

### 2. 缓存策略

```java
public class CachedSSTable extends SSTable {
    private final LRUCache<String, String> cache;           // LRU缓存存储热点数据
    private final AtomicLong hitCount = new AtomicLong(0);   // 缓存命中计数
    private final AtomicLong missCount = new AtomicLong(0);  // 缓存未命中计数
    
    // 构造带缓存的SSTable
    public CachedSSTable(String filePath, List<KeyValue> sortedData, int cacheSize) 
            throws IOException {
        super(filePath, sortedData);                         // 调用父类构造函数
        this.cache = new LRUCache<>(cacheSize);              // 创建指定大小的LRU缓存
    }
    
    // 重写get方法，加入缓存逻辑
    @Override
    public String get(String key) {
        // 首先检查缓存
        String cachedValue = cache.get(key);                 // 从缓存获取
        if (cachedValue != null) {
            hitCount.incrementAndGet();                      // 增加命中计数
            return cachedValue;                              // 返回缓存的值
        }
        
        // 缓存未命中，从SSTable读取
        String value = super.get(key);                       // 调用父类的get方法
        missCount.incrementAndGet();                         // 增加未命中计数
        
        // 如果找到了值，加入缓存
        if (value != null) {
            cache.put(key, value);                           // 将结果放入缓存
        }
        
        return value;                                        // 返回读取的值
    }
    
    // 获取缓存命中率
    public double getCacheHitRate() {
        long total = hitCount.get() + missCount.get();       // 计算总访问次数
        return total > 0 ? (double) hitCount.get() / total : 0.0;  // 计算命中率
    }
    
    // 获取缓存统计信息
    public String getCacheStats() {
        return String.format("Cache Stats: Hits=%d, Misses=%d, Hit Rate=%.2f%%",
                           hitCount.get(), missCount.get(), getCacheHitRate() * 100);
    }
}
```

**代码解释**: 缓存策略通过在内存中保存热点数据来减少磁盘访问。使用LRU（最近最少使用）策略管理缓存，确保最常访问的数据保留在内存中。命中和未命中计数器帮助监控缓存效果。当缓存命中时，直接返回结果；未命中时，从磁盘读取并将结果加入缓存，提高后续访问的性能。

## 实际应用场景

### 1. 高频写入场景

```java
public class HighVolumeSSTableExample {
    
    public static void main(String[] args) throws IOException {
        // 模拟从MemTable刷盘到SSTable
        List<KeyValue> memTableData = generateSortedData(10000);
        
        long startTime = System.currentTimeMillis();
        
        // 创建SSTable
        String filePath = "data/sstable_" + System.currentTimeMillis() + ".db";
        SSTable ssTable = new SSTable(filePath, memTableData);
        
        long writeTime = System.currentTimeMillis() - startTime;
        
        // 测试查询性能
        startTime = System.currentTimeMillis();
        int hitCount = 0;
        
        for (int i = 0; i < 1000; i++) {
            String key = "key_" + (i * 10); // 部分命中测试
            String value = ssTable.get(key);
            if (value != null) {
                hitCount++;
            }
        }
        
        long readTime = System.currentTimeMillis() - startTime;
        
        System.out.printf("SSTable性能测试:%n");
        System.out.printf("写入: %d条记录, 耗时: %dms%n", memTableData.size(), writeTime);
        System.out.printf("查询: 1000次查询, 耗时: %dms, 命中: %d次%n", 
                         readTime, hitCount);
        
        // 文件大小分析
        File file = new File(filePath);
        System.out.printf("文件大小: %.2f KB%n", file.length() / 1024.0);
    }
    
    private static List<KeyValue> generateSortedData(int count) {
        List<KeyValue> data = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            String key = "key_" + String.format("%06d", i);
            String value = "value_" + i + "_" + System.currentTimeMillis();
            data.add(new KeyValue(key, value));
        }
        
        return data;
    }
}
```

### 2. 范围查询支持

```java
public class RangeQuerySSTable extends SSTable {
    
    public List<KeyValue> getRange(String startKey, String endKey) {
        List<KeyValue> result = new ArrayList<>();
        
        // 找到起始位置
        int startIndex = findStartIndex(startKey);
        if (startIndex == -1) {
            return result;
        }
        
        // 收集范围内的数据
        for (int i = startIndex; i < sortedData.size(); i++) {
            KeyValue kv = sortedData.get(i);
            
            if (kv.getKey().compareTo(endKey) > 0) {
                break; // 超出范围
            }
            
            if (!kv.isDeleted()) {
                result.add(kv);
            }
        }
        
        return result;
    }
    
    private int findStartIndex(String startKey) {
        int left = 0;
        int right = sortedData.size() - 1;
        int result = -1;
        
        while (left <= right) {
            int mid = (left + right) / 2;
            String midKey = sortedData.get(mid).getKey();
            
            if (midKey.compareTo(startKey) >= 0) {
                result = mid;
                right = mid - 1;
            } else {
                left = mid + 1;
            }
        }
        
        return result;
    }
}
```

### 3. 并发读取优化

```java
public class ConcurrentSSTable extends SSTable {
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    
    @Override
    public String get(String key) {
        readLock.lock();
        try {
            return super.get(key);
        } finally {
            readLock.unlock();
        }
    }
    
    public List<String> getBatch(List<String> keys) {
        readLock.lock();
        try {
            List<String> results = new ArrayList<>();
            for (String key : keys) {
                results.add(super.get(key));
            }
            return results;
        } finally {
            readLock.unlock();
        }
    }
}
```

## 文件管理策略

### 1. 文件命名规范

```java
public class SSTableFileManager {
    
    public static String generateFileName(int level, long timestamp) {
        return String.format("sstable_L%d_%d.db", level, timestamp);
    }
    
    public static class SSTableMetadata {
        private final int level;
        private final long timestamp;
        private final String minKey;
        private final String maxKey;
        private final long fileSize;
        
        // 从文件名解析元数据
        public static SSTableMetadata fromFileName(String fileName) {
            String[] parts = fileName.replace(".db", "").split("_");
            int level = Integer.parseInt(parts[1].substring(1)); // 去掉L前缀
            long timestamp = Long.parseLong(parts[2]);
            
            return new SSTableMetadata(level, timestamp, null, null, 0);
        }
    }
}
```

### 2. 文件清理策略

```java
public class SSTableCleaner {
    private final String dataDirectory;
    
    public void cleanupOldFiles(long maxAge) throws IOException {
        File dir = new File(dataDirectory);
        File[] files = dir.listFiles((file, name) -> name.endsWith(".db"));
        
        if (files == null) return;
        
        long cutoffTime = System.currentTimeMillis() - maxAge;
        
        for (File file : files) {
            if (file.lastModified() < cutoffTime) {
                if (file.delete()) {
                    System.out.println("删除过期文件: " + file.getName());
                }
            }
        }
    }
    
    public void cleanupEmptyFiles() throws IOException {
        File dir = new File(dataDirectory);
        File[] files = dir.listFiles((file, name) -> name.endsWith(".db"));
        
        if (files == null) return;
        
        for (File file : files) {
            if (file.length() == 0) {
                if (file.delete()) {
                    System.out.println("删除空文件: " + file.getName());
                }
            }
        }
    }
}
```

## 性能监控

### 1. 读写统计

```java
public class SSTableMetrics {
    private final AtomicLong readCount = new AtomicLong(0);
    private final AtomicLong readTime = new AtomicLong(0);
    private final AtomicLong bloomFilterHits = new AtomicLong(0);
    private final AtomicLong bloomFilterMisses = new AtomicLong(0);
    
    public void recordRead(long duration, boolean bloomFilterHit) {
        readCount.incrementAndGet();
        readTime.addAndGet(duration);
        
        if (bloomFilterHit) {
            bloomFilterHits.incrementAndGet();
        } else {
            bloomFilterMisses.incrementAndGet();
        }
    }
    
    public double getAverageReadTime() {
        long reads = readCount.get();
        return reads > 0 ? (double) readTime.get() / reads : 0.0;
    }
    
    public double getBloomFilterEfficiency() {
        long total = bloomFilterHits.get() + bloomFilterMisses.get();
        return total > 0 ? (double) bloomFilterHits.get() / total : 0.0;
    }
    
    public String getStats() {
        return String.format(
            "读取次数: %d, 平均耗时: %.2fms, 布隆过滤器效率: %.2f%%",
            readCount.get(),
            getAverageReadTime(),
            getBloomFilterEfficiency() * 100
        );
    }
}
```

### 2. 文件系统监控

```java
public class FileSystemMonitor {
    
    public static void monitorDiskUsage(String dataDirectory) {
        File dir = new File(dataDirectory);
        long totalSpace = dir.getTotalSpace();
        long freeSpace = dir.getFreeSpace();
        long usedSpace = totalSpace - freeSpace;
        
        double usagePercent = (double) usedSpace / totalSpace * 100;
        
        System.out.printf("磁盘使用情况:%n");
        System.out.printf("总空间: %.2f GB%n", totalSpace / (1024.0 * 1024.0 * 1024.0));
        System.out.printf("已用空间: %.2f GB (%.1f%%)%n", 
                         usedSpace / (1024.0 * 1024.0 * 1024.0), usagePercent);
        System.out.printf("剩余空间: %.2f GB%n", freeSpace / (1024.0 * 1024.0 * 1024.0));
        
        if (usagePercent > 90) {
            System.out.println("警告: 磁盘空间不足，建议清理或扩容！");
        }
    }
}
```

## 常见问题和解决方案

### 1. 文件损坏检测

```java
public class SSTableValidator {
    
    public static boolean validateFile(String filePath) {
        try {
            SSTable.loadFromFile(filePath);
            return true;
        } catch (Exception e) {
            System.err.println("文件损坏: " + filePath + ", 错误: " + e.getMessage());
            return false;
        }
    }
    
    public static void repairCorruptedFile(String filePath) throws IOException {
        String backupPath = filePath + ".backup";
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath));
             BufferedWriter writer = new BufferedWriter(new FileWriter(backupPath))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    // 尝试解析每一行
                    if (line.contains("|")) {
                        String[] parts = line.split("\\|");
                        if (parts.length == 4) {
                            writer.write(line);
                            writer.newLine();
                        }
                    }
                } catch (Exception e) {
                    // 跳过损坏的行
                    System.out.println("跳过损坏行: " + line);
                }
            }
        }
        
        // 替换原文件
        Files.move(Paths.get(backupPath), Paths.get(filePath), 
                  StandardCopyOption.REPLACE_EXISTING);
    }
}
```

### 2. 内存优化

```java
public class MemoryOptimizedSSTable {
    private static final int BUFFER_SIZE = 8192;
    
    // 使用流式读取减少内存占用
    public String getStreaming(String key) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new FileReader(filePath), BUFFER_SIZE)) {
            
            // 跳过条目数量行
            reader.readLine();
            
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(key + "|")) {
                    String[] parts = line.split("\\|");
                    if (parts[0].equals(key)) {
                        boolean deleted = Boolean.parseBoolean(parts[3]);
                        return deleted ? null : parts[1];
                    }
                }
            }
            
            return null;
        }
    }
}
```

## 小结

SSTable是LSM Tree的持久化存储层，具有以下关键特性：

1. **不可变性**: 确保数据一致性和线程安全
2. **有序性**: 支持高效查找和范围查询
3. **自包含**: 包含布隆过滤器和索引信息
4. **优化**: 多种性能优化技术

## 下一步学习

现在你已经理解了SSTable的设计，接下来我们将学习布隆过滤器的工作原理：

继续阅读：[第5章：布隆过滤器](05-bloom-filter.md)

---

## 思考题

1. 为什么SSTable要设计为不可变的？
2. 布隆过滤器如何提升SSTable的查询性能？
3. 如何在保持性能的同时减少SSTable的磁盘占用？

**下一章预告**: 我们将深入学习布隆过滤器的数学原理、哈希函数设计和性能优化。 