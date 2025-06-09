# 第9章：性能优化

## 性能优化策略

LSM Tree的性能优化主要关注以下几个方面：

### 1. 写入优化
- 批量写入
- 异步WAL
- MemTable大小调优
- 写入缓冲区

### 2. 读取优化  
- 布隆过滤器
- 缓存策略
- 索引优化
- 并行查询

### 3. 压缩优化
- 智能压缩调度
- 多线程压缩
- 选择性压缩
- 增量压缩

## 批量写入优化

```java
public class BatchWriter {
    private final LSMTree lsmTree;
    private final List<WriteOperation> batch = new ArrayList<>();
    private final int batchSize;
    
    public BatchWriter(LSMTree lsmTree, int batchSize) {
        this.lsmTree = lsmTree;
        this.batchSize = batchSize;
    }
    
    public void addWrite(String key, String value) throws IOException {
        batch.add(new WriteOperation(key, value, false));
        
        if (batch.size() >= batchSize) {
            flush();
        }
    }
    
    public void flush() throws IOException {
        if (batch.isEmpty()) return;
        
        Map<String, String> puts = new HashMap<>();
        Set<String> deletes = new HashSet<>();
        
        for (WriteOperation op : batch) {
            if (op.isDelete) {
                deletes.add(op.key);
            } else {
                puts.put(op.key, op.value);
            }
        }
        
        lsmTree.putBatch(puts);
        for (String key : deletes) {
            lsmTree.delete(key);
        }
        
        batch.clear();
    }
    
    private static class WriteOperation {
        final String key;
        final String value;
        final boolean isDelete;
        
        WriteOperation(String key, String value, boolean isDelete) {
            this.key = key;
            this.value = value;
            this.isDelete = isDelete;
        }
    }
}
```

## 缓存策略

```java
public class LSMTreeWithCache extends LSMTree {
    private final LRUCache<String, String> cache;
    
    public LSMTreeWithCache(String dataDirectory, int cacheSize) throws IOException {
        super(dataDirectory);
        this.cache = new LRUCache<>(cacheSize);
    }
    
    @Override
    public String get(String key) throws IOException {
        // 先查缓存
        String cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        
        // 查询LSM Tree
        String value = super.get(key);
        if (value != null) {
            cache.put(key, value);
        }
        
        return value;
    }
}
```

## 并行查询

```java
public class ParallelLSMTree extends LSMTree {
    private final ExecutorService queryExecutor;
    
    public ParallelLSMTree(String dataDirectory) throws IOException {
        super(dataDirectory);
        this.queryExecutor = Executors.newFixedThreadPool(4);
    }
    
    public Map<String, String> getBatch(List<String> keys) throws IOException {
        Map<String, String> results = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (String key : keys) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    String value = get(key);
                    if (value != null) {
                        results.put(key, value);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, queryExecutor);
            
            futures.add(future);
        }
        
        // 等待所有查询完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        return results;
    }
}
```

## 智能压缩调度

```java
public class SmartCompactionScheduler {
    private final CompactionStrategy strategy;
    private final AtomicLong lastCompactionTime = new AtomicLong(0);
    private final long minCompactionInterval = 60_000; // 1分钟
    
    public boolean shouldCompact(int level) {
        if (!strategy.needsCompaction(level)) {
            return false;
        }
        
        long now = System.currentTimeMillis();
        long lastTime = lastCompactionTime.get();
        
        // 限制压缩频率
        if (now - lastTime < minCompactionInterval) {
            return false;
        }
        
        // 检查系统负载
        return isSystemIdle();
    }
    
    private boolean isSystemIdle() {
        // 简化的负载检查
        return Runtime.getRuntime().availableProcessors() > 2;
    }
    
    public void recordCompaction() {
        lastCompactionTime.set(System.currentTimeMillis());
    }
}
```

## 性能监控

```java
public class PerformanceMonitor {
    private final AtomicLong readCount = new AtomicLong(0);
    private final AtomicLong writeCount = new AtomicLong(0);
    private final AtomicLong readTime = new AtomicLong(0);
    private final AtomicLong writeTime = new AtomicLong(0);
    
    public void recordRead(long duration) {
        readCount.incrementAndGet();
        readTime.addAndGet(duration);
    }
    
    public void recordWrite(long duration) {
        writeCount.incrementAndGet();
        writeTime.addAndGet(duration);
    }
    
    public String getStats() {
        long reads = readCount.get();
        long writes = writeCount.get();
        
        double avgReadTime = reads > 0 ? (double) readTime.get() / reads : 0.0;
        double avgWriteTime = writes > 0 ? (double) writeTime.get() / writes : 0.0;
        
        return String.format(
            "性能统计: 读取%,d次(%.2fμs), 写入%,d次(%.2fμs)",
            reads, avgReadTime / 1000.0,
            writes, avgWriteTime / 1000.0
        );
    }
}
```

## 小结

性能优化的关键点：

1. **批量操作**: 减少系统调用开销
2. **缓存策略**: 提升热点数据访问速度  
3. **并行处理**: 充分利用多核CPU
4. **智能调度**: 根据系统状态调整策略
5. **监控统计**: 持续优化和调整

## 下一步学习

继续阅读：[第10章：完整示例](10-complete-example.md)

---

## 思考题

1. 如何选择合适的批量大小？
2. 缓存替换策略如何影响性能？
3. 并行查询的线程数如何确定？ 