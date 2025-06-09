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

### 1. Size-Tiered 压缩

我们的实现采用**Size-Tiered**策略：

```
Level 0: [4个SSTable文件] → 合并到Level 1
Level 1: [4个合并文件] → 合并到Level 2  
Level 2: [16个文件] → 合并到Level 3
...

规则：
- 每层最多N个文件
- 文件大小逐层增长
- 触发阈值：文件数量
```

## 压缩策略实现

### 核心实现

```java
package com.brianxiadong.lsmtree;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CompactionStrategy {
    private final String dataDirectory;
    private final int maxFilesPerLevel;
    private final Map<Integer, List<String>> levelFiles;
    
    public CompactionStrategy(String dataDirectory, int maxFilesPerLevel) {
        this.dataDirectory = dataDirectory;
        this.maxFilesPerLevel = maxFilesPerLevel;
        this.levelFiles = new ConcurrentHashMap<>();
    }
    
    // 添加新的SSTable文件到Level 0
    public void addSSTable(String filePath) {
        levelFiles.computeIfAbsent(0, k -> new ArrayList<>()).add(filePath);
    }
    
    // 检查是否需要压缩
    public boolean needsCompaction(int level) {
        List<String> files = levelFiles.get(level);
        return files != null && files.size() >= maxFilesPerLevel;
    }
    
    // 执行压缩
    public void compact(int level) throws IOException {
        if (!needsCompaction(level)) {
            return;
        }
        
        List<String> filesToCompact = new ArrayList<>(levelFiles.get(level));
        String compactedFile = performCompaction(filesToCompact, level + 1);
        
        // 更新文件层级
        levelFiles.get(level).clear();
        levelFiles.computeIfAbsent(level + 1, k -> new ArrayList<>()).add(compactedFile);
        
        // 删除旧文件
        cleanupOldFiles(filesToCompact);
        
        // 递归检查下一层
        if (needsCompaction(level + 1)) {
            compact(level + 1);
        }
    }
}
```

### 压缩执行器

```java
private String performCompaction(List<String> inputFiles, int targetLevel) throws IOException {
    System.out.printf("开始压缩 %d 个文件到 Level %d%n", inputFiles.size(), targetLevel);
    
    // 1. 加载所有输入文件
    List<SSTable> inputTables = new ArrayList<>();
    for (String filePath : inputFiles) {
        inputTables.add(SSTable.loadFromFile(filePath));
    }
    
    // 2. 合并排序所有数据
    List<KeyValue> mergedData = mergeSSTableData(inputTables);
    
    // 3. 去重和清理
    List<KeyValue> cleanedData = deduplicateAndClean(mergedData);
    
    // 4. 生成输出文件名
    String outputFile = generateCompactedFileName(targetLevel);
    
    // 5. 创建新的SSTable
    SSTable compactedTable = new SSTable(outputFile, cleanedData);
    
    System.out.printf("压缩完成: %s (清理前: %d条, 清理后: %d条)%n", 
                     outputFile, mergedData.size(), cleanedData.size());
    
    return outputFile;
}
```

### 数据合并算法

```java
private List<KeyValue> mergeSSTableData(List<SSTable> tables) {
    // 使用多路归并排序
    PriorityQueue<SSTableIterator> heap = new PriorityQueue<>(
        Comparator.comparing(iter -> iter.current().getKey())
    );
    
    // 初始化所有迭代器
    for (SSTable table : tables) {
        SSTableIterator iter = table.iterator();
        if (iter.hasNext()) {
            iter.next();
            heap.offer(iter);
        }
    }
    
    List<KeyValue> merged = new ArrayList<>();
    
    while (!heap.isEmpty()) {
        SSTableIterator iter = heap.poll();
        KeyValue current = iter.current();
        merged.add(current);
        
        if (iter.hasNext()) {
            iter.next();
            heap.offer(iter);
        }
    }
    
    return merged;
}

// SSTable迭代器
private static class SSTableIterator {
    private final List<KeyValue> data;
    private int index = -1;
    
    public SSTableIterator(SSTable table) {
        this.data = table.getAllData(); // 获取所有数据
    }
    
    public boolean hasNext() {
        return index + 1 < data.size();
    }
    
    public KeyValue next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return data.get(++index);
    }
    
    public KeyValue current() {
        if (index < 0 || index >= data.size()) {
            return null;
        }
        return data.get(index);
    }
}
```

### 去重和清理

```java
private List<KeyValue> deduplicateAndClean(List<KeyValue> sortedData) {
    if (sortedData.isEmpty()) {
        return sortedData;
    }
    
    List<KeyValue> cleaned = new ArrayList<>();
    String lastKey = null;
    KeyValue lastKV = null;
    
    for (KeyValue kv : sortedData) {
        String currentKey = kv.getKey();
        
        if (!currentKey.equals(lastKey)) {
            // 新键：添加上一个键的最新版本
            if (lastKV != null && !lastKV.isDeleted()) {
                cleaned.add(lastKV);
            }
            lastKey = currentKey;
            lastKV = kv;
        } else {
            // 相同键：保留最新版本（时间戳最大）
            if (kv.getTimestamp() > lastKV.getTimestamp()) {
                lastKV = kv;
            }
        }
    }
    
    // 添加最后一个键
    if (lastKV != null && !lastKV.isDeleted()) {
        cleaned.add(lastKV);
    }
    
    return cleaned;
}
```

## 高级压缩策略

### 1. Leveled 压缩

```java
public class LeveledCompactionStrategy extends CompactionStrategy {
    private final long[] maxLevelSize;
    private final double sizeTierRatio;
    
    public LeveledCompactionStrategy(String dataDirectory, double sizeTierRatio) {
        super(dataDirectory, 10); // 每层最多10个文件
        this.sizeTierRatio = sizeTierRatio;
        this.maxLevelSize = calculateLevelSizes();
    }
    
    private long[] calculateLevelSizes() {
        long[] sizes = new long[10]; // 支持10层
        sizes[0] = 10 * 1024 * 1024; // Level 0: 10MB
        
        for (int i = 1; i < sizes.length; i++) {
            sizes[i] = (long) (sizes[i - 1] * sizeTierRatio);
        }
        
        return sizes;
    }
    
    @Override
    public boolean needsCompaction(int level) {
        List<String> files = levelFiles.get(level);
        if (files == null || files.isEmpty()) {
            return false;
        }
        
        if (level == 0) {
            // Level 0 按文件数量判断
            return files.size() >= maxFilesPerLevel;
        }
        
        // 其他层按总大小判断
        long totalSize = calculateLevelSize(level);
        return totalSize > maxLevelSize[level];
    }
    
    private long calculateLevelSize(int level) {
        List<String> files = levelFiles.get(level);
        if (files == null) return 0;
        
        return files.stream()
                .mapToLong(this::getFileSize)
                .sum();
    }
    
    private long getFileSize(String filePath) {
        try {
            return new File(filePath).length();
        } catch (Exception e) {
            return 0;
        }
    }
}
```

### 2. 选择性压缩

```java
public class SelectiveCompactionStrategy extends CompactionStrategy {
    private final double deadRatioThreshold;
    
    public SelectiveCompactionStrategy(String dataDirectory, double deadRatioThreshold) {
        super(dataDirectory, 4);
        this.deadRatioThreshold = deadRatioThreshold;
    }
    
    @Override
    public void compact(int level) throws IOException {
        List<String> candidates = selectCompactionCandidates(level);
        
        if (candidates.isEmpty()) {
            return;
        }
        
        String compactedFile = performCompaction(candidates, level + 1);
        
        // 更新文件结构
        updateFileLevels(candidates, compactedFile, level);
    }
    
    private List<String> selectCompactionCandidates(int level) {
        List<String> files = levelFiles.get(level);
        if (files == null) return new ArrayList<>();
        
        List<CompactionCandidate> candidates = new ArrayList<>();
        
        for (String file : files) {
            double deadRatio = calculateDeadRatio(file);
            CompactionCandidate candidate = new CompactionCandidate(file, deadRatio);
            candidates.add(candidate);
        }
        
        // 按死亡率排序，优先压缩死亡率高的文件
        candidates.sort((a, b) -> Double.compare(b.deadRatio, a.deadRatio));
        
        List<String> selected = new ArrayList<>();
        for (CompactionCandidate candidate : candidates) {
            if (candidate.deadRatio > deadRatioThreshold) {
                selected.add(candidate.filePath);
            }
            
            if (selected.size() >= maxFilesPerLevel) {
                break;
            }
        }
        
        return selected;
    }
    
    private double calculateDeadRatio(String filePath) {
        try {
            SSTable table = SSTable.loadFromFile(filePath);
            List<KeyValue> data = table.getAllData();
            
            if (data.isEmpty()) return 1.0;
            
            int deletedCount = 0;
            Map<String, Integer> keyVersions = new HashMap<>();
            
            for (KeyValue kv : data) {
                if (kv.isDeleted()) {
                    deletedCount++;
                }
                keyVersions.merge(kv.getKey(), 1, Integer::sum);
            }
            
            // 死亡率 = (删除条目 + 重复键) / 总条目
            int duplicates = keyVersions.values().stream()
                    .mapToInt(count -> Math.max(0, count - 1))
                    .sum();
            
            return (double) (deletedCount + duplicates) / data.size();
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    private static class CompactionCandidate {
        final String filePath;
        final double deadRatio;
        
        CompactionCandidate(String filePath, double deadRatio) {
            this.filePath = filePath;
            this.deadRatio = deadRatio;
        }
    }
}
```

### 3. 并行压缩

```java
public class ParallelCompactionStrategy extends CompactionStrategy {
    private final ExecutorService compactionExecutor;
    private final int parallelismLevel;
    
    public ParallelCompactionStrategy(String dataDirectory, int parallelismLevel) {
        super(dataDirectory, 4);
        this.parallelismLevel = parallelismLevel;
        this.compactionExecutor = Executors.newFixedThreadPool(parallelismLevel);
    }
    
    @Override
    public void compact(int level) throws IOException {
        List<String> files = levelFiles.get(level);
        if (files == null || files.size() < maxFilesPerLevel) {
            return;
        }
        
        // 将文件分组进行并行压缩
        List<List<String>> fileGroups = partitionFiles(files);
        List<CompletableFuture<String>> futures = new ArrayList<>();
        
        for (List<String> group : fileGroups) {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return performCompaction(group, level + 1);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, compactionExecutor);
            
            futures.add(future);
        }
        
        // 等待所有压缩完成
        List<String> compactedFiles = new ArrayList<>();
        for (CompletableFuture<String> future : futures) {
            try {
                compactedFiles.add(future.get());
            } catch (Exception e) {
                throw new IOException("并行压缩失败", e);
            }
        }
        
        // 更新文件层级
        levelFiles.get(level).clear();
        levelFiles.computeIfAbsent(level + 1, k -> new ArrayList<>()).addAll(compactedFiles);
        
        cleanupOldFiles(files);
    }
    
    private List<List<String>> partitionFiles(List<String> files) {
        List<List<String>> groups = new ArrayList<>();
        int groupSize = Math.max(1, files.size() / parallelismLevel);
        
        for (int i = 0; i < files.size(); i += groupSize) {
            int end = Math.min(i + groupSize, files.size());
            groups.add(new ArrayList<>(files.subList(i, end)));
        }
        
        return groups;
    }
    
    public void shutdown() {
        compactionExecutor.shutdown();
        try {
            if (!compactionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                compactionExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            compactionExecutor.shutdownNow();
        }
    }
}
```

## 性能优化

### 1. 压缩调度器

```java
public class CompactionScheduler {
    private final CompactionStrategy strategy;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean compactionInProgress = new AtomicBoolean(false);
    
    public CompactionScheduler(CompactionStrategy strategy) {
        this.strategy = strategy;
        this.scheduler = Executors.newScheduledThreadPool(1);
    }
    
    public void startBackgroundCompaction() {
        scheduler.scheduleWithFixedDelay(
            this::performBackgroundCompaction,
            10, // 初始延迟
            30, // 执行间隔
            TimeUnit.SECONDS
        );
    }
    
    private void performBackgroundCompaction() {
        if (compactionInProgress.compareAndSet(false, true)) {
            try {
                compactAllLevels();
            } catch (Exception e) {
                System.err.println("后台压缩失败: " + e.getMessage());
            } finally {
                compactionInProgress.set(false);
            }
        }
    }
    
    private void compactAllLevels() throws IOException {
        for (int level = 0; level < 10; level++) {
            if (strategy.needsCompaction(level)) {
                long startTime = System.currentTimeMillis();
                strategy.compact(level);
                long duration = System.currentTimeMillis() - startTime;
                
                System.out.printf("Level %d 压缩完成，耗时: %d ms%n", level, duration);
            }
        }
    }
    
    public boolean triggerManualCompaction() {
        if (compactionInProgress.compareAndSet(false, true)) {
            try {
                compactAllLevels();
                return true;
            } catch (Exception e) {
                System.err.println("手动压缩失败: " + e.getMessage());
                return false;
            } finally {
                compactionInProgress.set(false);
            }
        }
        return false; // 压缩正在进行中
    }
    
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
    }
}
```

### 2. 压缩统计

```java
public class CompactionMetrics {
    private final AtomicLong compactionCount = new AtomicLong(0);
    private final AtomicLong totalCompactionTime = new AtomicLong(0);
    private final AtomicLong bytesCompacted = new AtomicLong(0);
    private final AtomicLong filesCompacted = new AtomicLong(0);
    private final Map<Integer, AtomicLong> levelCompactionCounts = new ConcurrentHashMap<>();
    
    public void recordCompaction(int level, long duration, long bytesProcessed, int fileCount) {
        compactionCount.incrementAndGet();
        totalCompactionTime.addAndGet(duration);
        bytesCompacted.addAndGet(bytesProcessed);
        filesCompacted.addAndGet(fileCount);
        
        levelCompactionCounts.computeIfAbsent(level, k -> new AtomicLong(0))
                .incrementAndGet();
    }
    
    public String getCompactionStats() {
        long totalCompactions = compactionCount.get();
        double avgTime = totalCompactions > 0 ? 
                (double) totalCompactionTime.get() / totalCompactions : 0.0;
        
        StringBuilder stats = new StringBuilder();
        stats.append(String.format("压缩统计:%n"));
        stats.append(String.format("  总压缩次数: %,d%n", totalCompactions));
        stats.append(String.format("  平均耗时: %.2f ms%n", avgTime));
        stats.append(String.format("  压缩数据量: %.2f MB%n", bytesCompacted.get() / (1024.0 * 1024.0)));
        stats.append(String.format("  压缩文件数: %,d%n", filesCompacted.get()));
        
        stats.append("各层压缩次数:%n");
        levelCompactionCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> stats.append(String.format("  Level %d: %,d%n", 
                        entry.getKey(), entry.getValue().get())));
        
        return stats.toString();
    }
    
    public double getCompactionThroughput() {
        long totalTime = totalCompactionTime.get();
        long totalBytes = bytesCompacted.get();
        
        if (totalTime == 0) return 0.0;
        
        // MB/s
        return (totalBytes / (1024.0 * 1024.0)) / (totalTime / 1000.0);
    }
}
```

## 实际应用场景

### 1. 写密集型场景

```java
public class WriteIntensiveCompactionStrategy extends CompactionStrategy {
    
    public WriteIntensiveCompactionStrategy(String dataDirectory) {
        super(dataDirectory, 8); // 更多文件才触发压缩
    }
    
    @Override
    public boolean needsCompaction(int level) {
        List<String> files = levelFiles.get(level);
        if (files == null) return false;
        
        if (level == 0) {
            // Level 0 允许更多文件，减少压缩频率
            return files.size() >= maxFilesPerLevel * 2;
        }
        
        return files.size() >= maxFilesPerLevel;
    }
    
    // 延迟压缩：在系统空闲时进行
    public void performDelayedCompaction() throws IOException {
        if (isSystemIdle()) {
            for (int level = 0; level < 5; level++) {
                if (needsCompaction(level)) {
                    compact(level);
                }
            }
        }
    }
    
    private boolean isSystemIdle() {
        // 检查系统负载、I/O使用率等
        return System.currentTimeMillis() % 60000 < 5000; // 简化：每分钟前5秒认为空闲
    }
}
```

### 2. 读密集型场景

```java
public class ReadIntensiveCompactionStrategy extends CompactionStrategy {
    
    public ReadIntensiveCompactionStrategy(String dataDirectory) {
        super(dataDirectory, 2); // 更少文件触发压缩
    }
    
    @Override
    public void compact(int level) throws IOException {
        if (!needsCompaction(level)) {
            return;
        }
        
        // 积极压缩以减少读放大
        List<String> filesToCompact = new ArrayList<>(levelFiles.get(level));
        
        // 包含相邻层文件一起压缩
        if (level > 0) {
            List<String> lowerLevelFiles = levelFiles.get(level - 1);
            if (lowerLevelFiles != null && !lowerLevelFiles.isEmpty()) {
                filesToCompact.addAll(lowerLevelFiles);
                levelFiles.get(level - 1).clear();
            }
        }
        
        String compactedFile = performCompaction(filesToCompact, level + 1);
        
        levelFiles.get(level).clear();
        levelFiles.computeIfAbsent(level + 1, k -> new ArrayList<>()).add(compactedFile);
        
        cleanupOldFiles(filesToCompact);
    }
}
```

### 3. 混合负载场景

```java
public class AdaptiveCompactionStrategy extends CompactionStrategy {
    private final CircularBuffer<OperationStats> recentStats;
    private CompactionStrategy currentStrategy;
    
    public AdaptiveCompactionStrategy(String dataDirectory) {
        super(dataDirectory, 4);
        this.recentStats = new CircularBuffer<>(100);
        this.currentStrategy = this;
    }
    
    public void recordOperation(boolean isWrite) {
        recentStats.add(new OperationStats(isWrite, System.currentTimeMillis()));
        adaptStrategy();
    }
    
    private void adaptStrategy() {
        double writeRatio = calculateWriteRatio();
        
        if (writeRatio > 0.7) {
            // 写密集，采用写优化策略
            currentStrategy = new WriteIntensiveCompactionStrategy(dataDirectory);
        } else if (writeRatio < 0.3) {
            // 读密集，采用读优化策略
            currentStrategy = new ReadIntensiveCompactionStrategy(dataDirectory);
        }
        // 否则使用默认策略
    }
    
    private double calculateWriteRatio() {
        List<OperationStats> recent = recentStats.getRecent(50);
        if (recent.isEmpty()) return 0.5;
        
        long writeCount = recent.stream()
                .mapToLong(stat -> stat.isWrite ? 1 : 0)
                .sum();
        
        return (double) writeCount / recent.size();
    }
    
    @Override
    public void compact(int level) throws IOException {
        currentStrategy.compact(level);
    }
    
    private static class OperationStats {
        final boolean isWrite;
        final long timestamp;
        
        OperationStats(boolean isWrite, long timestamp) {
            this.isWrite = isWrite;
            this.timestamp = timestamp;
        }
    }
    
    // 简单的循环缓冲区实现
    private static class CircularBuffer<T> {
        private final Object[] buffer;
        private int head = 0;
        private int size = 0;
        
        CircularBuffer(int capacity) {
            this.buffer = new Object[capacity];
        }
        
        synchronized void add(T item) {
            buffer[head] = item;
            head = (head + 1) % buffer.length;
            if (size < buffer.length) {
                size++;
            }
        }
        
        @SuppressWarnings("unchecked")
        synchronized List<T> getRecent(int count) {
            List<T> result = new ArrayList<>();
            int actualCount = Math.min(count, size);
            
            for (int i = 0; i < actualCount; i++) {
                int index = (head - 1 - i + buffer.length) % buffer.length;
                result.add((T) buffer[index]);
            }
            
            return result;
        }
    }
}
```

## 压缩优化技术

### 1. 增量压缩

```java
public class IncrementalCompactionStrategy extends CompactionStrategy {
    private final Map<String, Long> lastCompactionTime = new ConcurrentHashMap<>();
    private final long compactionInterval = 3600_000; // 1小时
    
    @Override
    public boolean needsCompaction(int level) {
        if (!super.needsCompaction(level)) {
            return false;
        }
        
        String levelKey = "level_" + level;
        long lastTime = lastCompactionTime.getOrDefault(levelKey, 0L);
        long now = System.currentTimeMillis();
        
        // 避免频繁压缩
        return now - lastTime > compactionInterval;
    }
    
    @Override
    public void compact(int level) throws IOException {
        super.compact(level);
        lastCompactionTime.put("level_" + level, System.currentTimeMillis());
    }
}
```

### 2. 部分压缩

```java
public class PartialCompactionStrategy extends CompactionStrategy {
    
    public void compactPartial(int level, Set<String> keyRanges) throws IOException {
        List<String> files = levelFiles.get(level);
        if (files == null) return;
        
        // 只压缩包含指定键范围的文件
        List<String> candidateFiles = new ArrayList<>();
        
        for (String file : files) {
            if (fileContainsKeyRanges(file, keyRanges)) {
                candidateFiles.add(file);
            }
        }
        
        if (candidateFiles.size() >= 2) {
            String compactedFile = performCompaction(candidateFiles, level + 1);
            
            // 更新文件列表
            files.removeAll(candidateFiles);
            levelFiles.computeIfAbsent(level + 1, k -> new ArrayList<>()).add(compactedFile);
            
            cleanupOldFiles(candidateFiles);
        }
    }
    
    private boolean fileContainsKeyRanges(String filePath, Set<String> keyRanges) {
        try {
            SSTable table = SSTable.loadFromFile(filePath);
            for (String keyRange : keyRanges) {
                if (table.get(keyRange) != null) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
```

## 小结

压缩策略是LSM Tree性能的关键：

1. **文件管理**: 控制文件数量和大小
2. **空间回收**: 清理冗余和删除的数据
3. **性能平衡**: 在读写性能间找到平衡
4. **自适应**: 根据负载模式调整策略

## 下一步学习

现在你已经理解了压缩策略，接下来我们将学习LSM Tree主体的实现：

继续阅读：[第8章：LSM Tree 主体](08-lsm-tree-main.md)

---

## 思考题

1. 为什么需要多层压缩？
2. 如何选择合适的压缩触发条件？
3. 压缩过程中如何保证数据一致性？

**下一章预告**: 我们将学习如何将所有组件整合成完整的LSM Tree系统。 