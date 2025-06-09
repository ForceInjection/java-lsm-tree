# 第8章：LSM Tree 主程序实现

## 8.1 主程序架构设计

LSM Tree的主程序需要协调各个组件的工作，包括MemTable、SSTable、WAL、布隆过滤器和Compaction策略。

### 核心架构设计

```java
public class LSMTree {
    private final MemTable memTable;
    private final WAL wal;
    private final List<SSTable> sstables;
    private final CompactionStrategy compactionStrategy;
    private final BloomFilter bloomFilter;
    private final String dataDirectory;
    
    // 配置参数
    private final int memTableThreshold;
    private final int maxLevels;
    
    public LSMTree(String dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.memTableThreshold = 1000;
        this.maxLevels = 7;
        
        this.memTable = new MemTable();
        this.wal = new WAL(dataDirectory + "/wal");
        this.sstables = new ArrayList<>();
        this.compactionStrategy = new TieredCompactionStrategy();
        this.bloomFilter = new BloomFilter(10000, 0.01);
        
        initializeFromDisk();
    }
}
```

## 8.2 数据写入流程

### 写入操作实现

```java
public void put(String key, String value) {
    try {
        // 第一步：写WAL确保持久性，先写日志保证数据不丢失
        wal.append(new WALEntry("PUT", key, value, System.currentTimeMillis()));
        
        // 第二步：写入MemTable，更新内存中的数据
        memTable.put(key, value);
        
        // 第三步：检查是否需要flush，避免MemTable过大
        if (memTable.size() >= memTableThreshold) {
            flushMemTable();                               // 触发刷盘操作
        }
        
    } catch (Exception e) {
        // 统一异常处理，包装为运行时异常
        throw new RuntimeException("写入失败: " + e.getMessage(), e);
    }
}

// 将MemTable内容刷盘到SSTable文件
private void flushMemTable() {
    try {
        // 生成唯一的SSTable文件名，使用时间戳确保唯一性
        String filename = dataDirectory + "/sstable_" + System.currentTimeMillis() + ".db";
        // 从MemTable创建新的SSTable文件
        SSTable sstable = SSTable.createFromMemTable(memTable, filename);
        // 将新SSTable添加到SSTable列表中
        sstables.add(sstable);
        
        // 清空MemTable和WAL，为新写入腾出空间
        memTable.clear();                                  // 清空内存表
        wal.clear();                                       // 清空WAL日志
        
        // 触发compaction检查，优化存储结构
        compactionStrategy.checkAndCompact(sstables);      // 检查是否需要合并SSTable
        
    } catch (Exception e) {
        // Flush操作失败的异常处理
        throw new RuntimeException("Flush失败: " + e.getMessage(), e);
    }
}
```

**代码解释**: 写入操作采用WAL-first策略，确保数据持久性。首先写WAL日志，然后更新MemTable，最后检查是否需要刷盘。这种顺序保证了即使系统崩溃，也能通过WAL恢复数据。刷盘操作将MemTable内容持久化为SSTable，并触发compaction检查以优化存储效率。

### 删除操作实现

```java
public void delete(String key) {
    try {
        // 第一步：写WAL记录删除操作，确保删除操作持久化
        wal.append(new WALEntry("DELETE", key, null, System.currentTimeMillis()));
        
        // 第二步：在MemTable中标记删除，插入墓碑记录
        memTable.delete(key);                              // 创建删除标记
        
        // 第三步：检查flush条件，删除操作也会增加MemTable大小
        if (memTable.size() >= memTableThreshold) {
            flushMemTable();                               // 触发刷盘操作
        }
        
    } catch (Exception e) {
        // 删除操作的异常处理
        throw new RuntimeException("删除失败: " + e.getMessage(), e);
    }
}
```

**代码解释**: 删除操作遵循LSM Tree的逻辑删除原则。不是立即物理删除数据，而是插入一个墓碑标记。这种设计保持了LSM Tree的不可变性，删除操作会在后续的compaction过程中真正清理数据。

## 8.3 数据读取流程

### 读取操作实现

```java
public String get(String key) {
    try {
        // 第一步：优先从MemTable查找（最新数据）
        String value = memTable.get(key);                  // 从内存表获取
        if (value != null) {
            // 检查是否为删除标记，返回相应结果
            return "DELETED".equals(value) ? null : value;  // 处理删除标记
        }
        
        // 第二步：从SSTable查找（按时间倒序，新文件优先）
        for (int i = sstables.size() - 1; i >= 0; i--) {
            SSTable sstable = sstables.get(i);             // 获取SSTable
            
            // 使用布隆过滤器快速过滤不存在的键
            if (!sstable.mightContain(key)) {
                continue;                                   // 布隆过滤器说不存在，跳过
            }
            
            // 布隆过滤器说可能存在，进行实际查找
            value = sstable.get(key);                       // 从SSTable查找
            if (value != null) {
                // 检查删除标记并返回结果
                return "DELETED".equals(value) ? null : value;
            }
        }
        
        return null;                                        // 所有地方都没找到
        
    } catch (Exception e) {
        // 读取操作的异常处理
        throw new RuntimeException("读取失败: " + e.getMessage(), e);
    }
}
```

**代码解释**: 读取操作采用分层查找策略。首先查找MemTable（包含最新数据），然后按时间倒序查找SSTable文件（新文件优先，因为包含更新的数据）。布隆过滤器用于快速过滤不存在的键，避免无效的磁盘访问。每个层级都要检查删除标记，确保已删除的数据不会被返回。

### 范围查询实现

```java
public List<KeyValue> scan(String startKey, String endKey) {
    List<KeyValue> result = new ArrayList<>();              // 存储查询结果
    Set<String> deletedKeys = new HashSet<>();              // 跟踪已删除的键
    
    try {
        // 第一步：从MemTable收集数据（最新数据优先）
        result.addAll(memTable.scan(startKey, endKey));     // 获取MemTable中的数据
        collectDeletedKeys(result, deletedKeys);            // 收集删除标记的键
        
        // 第二步：从SSTable收集数据（按时间倒序合并）
        for (int i = sstables.size() - 1; i >= 0; i--) {
            List<KeyValue> sstableData = sstables.get(i).scan(startKey, endKey);
            // 合并新数据到结果中，避免重复键
            mergeScanResults(result, sstableData, deletedKeys);
        }
        
        // 第三步：移除已删除的键，清理最终结果
        result.removeIf(kv -> deletedKeys.contains(kv.getKey()));
        
        return result;                                      // 返回最终结果
        
    } catch (Exception e) {
        // 范围查询的异常处理
        throw new RuntimeException("范围查询失败: " + e.getMessage(), e);
    }
}

// 收集结果中标记为删除的键
private void collectDeletedKeys(List<KeyValue> result, Set<String> deletedKeys) {
    for (KeyValue kv : result) {
        if (kv.isDeleted()) {                               // 检查删除标记
            deletedKeys.add(kv.getKey());                   // 记录已删除的键
        }
    }
}

// 合并扫描结果，避免重复键并跟踪删除状态
private void mergeScanResults(List<KeyValue> result, List<KeyValue> newData, Set<String> deletedKeys) {
    // 创建现有键的映射，用于快速查找
    Map<String, KeyValue> existingKeys = result.stream()
        .collect(Collectors.toMap(KeyValue::getKey, kv -> kv));
    
    // 遍历新数据，只添加不重复的键
    for (KeyValue kv : newData) {
        if (!existingKeys.containsKey(kv.getKey())) {       // 键不存在才添加
            result.add(kv);                                 // 添加新键值对
            if (kv.isDeleted()) {                           // 检查是否为删除标记
                deletedKeys.add(kv.getKey());               // 记录删除的键
            }
        }
    }
}
```

**代码解释**: 范围查询需要合并多个数据源的结果。首先从MemTable获取最新数据，然后按时间倒序遍历SSTable文件。使用Set记录删除的键，Map避免重复键的添加。最后过滤掉所有已删除的键，返回有效的结果集。这种设计确保了范围查询的正确性和高效性。

## 8.4 系统初始化与恢复

### 从磁盘初始化

```java
private void initializeFromDisk() {
    try {
        // 第一步：确保数据目录存在
        Files.createDirectories(Paths.get(dataDirectory)); // 创建目录（如果不存在）
        
        // 第二步：加载已存在的SSTable文件
        loadExistingSSTables();                             // 恢复SSTable列表
        
        // 第三步：从WAL恢复未持久化的数据
        recoverFromWAL();                                   // 恢复MemTable状态
        
    } catch (Exception e) {
        // 初始化失败的异常处理
        throw new RuntimeException("初始化失败: " + e.getMessage(), e);
    }
}

// 加载磁盘上现有的SSTable文件
private void loadExistingSSTables() throws IOException {
    Path dataPath = Paths.get(dataDirectory);              // 获取数据目录路径
    
    // 扫描目录中的.db文件并排序加载
    Files.list(dataPath)
        .filter(path -> path.toString().endsWith(".db"))   // 过滤SSTable文件
        .sorted()                                           // 按文件名排序（时间顺序）
        .forEach(path -> {
            try {
                SSTable sstable = SSTable.load(path.toString()); // 加载SSTable
                sstables.add(sstable);                      // 添加到SSTable列表
            } catch (Exception e) {
                // 单个文件加载失败不影响整体启动
                System.err.println("加载SSTable失败: " + path + ", " + e.getMessage());
            }
        });
}

// 从WAL日志恢复MemTable状态
private void recoverFromWAL() throws IOException {
    List<WALEntry> entries = wal.recover();                // 读取WAL条目
    
    // 重放WAL中的所有操作
    for (WALEntry entry : entries) {
        switch (entry.getOperation()) {                     // 根据操作类型处理
            case "PUT":
                memTable.put(entry.getKey(), entry.getValue()); // 重放PUT操作
                break;
            case "DELETE":
                memTable.delete(entry.getKey());           // 重放DELETE操作
                break;
            default:
                // 未知操作类型的警告
                System.err.println("未知的WAL操作类型: " + entry.getOperation());
        }
    }
}
```

**代码解释**: 系统初始化分为三个阶段：目录创建、SSTable加载和WAL恢复。SSTable文件按名称排序加载（通常按时间顺序），确保正确的层级关系。WAL恢复通过重放日志条目来恢复MemTable状态，这对系统的崩溃恢复能力至关重要。异常处理确保单个文件的问题不会阻止整个系统启动。

## 8.5 后台任务管理

### Compaction调度器

```java
public class CompactionScheduler {
    private final ScheduledExecutorService scheduler;
    private final LSMTree lsmTree;
    
    public CompactionScheduler(LSMTree lsmTree) {
        this.lsmTree = lsmTree;
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        // 每10分钟检查一次compaction
        scheduler.scheduleAtFixedRate(this::checkCompaction, 10, 10, TimeUnit.MINUTES);
        
        // 每小时检查一次清理工作
        scheduler.scheduleAtFixedRate(this::cleanup, 60, 60, TimeUnit.MINUTES);
    }
    
    private void checkCompaction() {
        try {
            lsmTree.getCompactionStrategy().checkAndCompact(lsmTree.getSSTables());
        } catch (Exception e) {
            System.err.println("Compaction检查失败: " + e.getMessage());
        }
    }
    
    private void cleanup() {
        try {
            // 清理过期的临时文件
            cleanupTempFiles();
            
            // 清理过期的WAL文件
            lsmTree.getWAL().cleanup();
            
        } catch (Exception e) {
            System.err.println("清理任务失败: " + e.getMessage());
        }
    }
}
```

## 8.6 线程安全与并发控制

### 读写锁实现

```java
public class ThreadSafeLSMTree {
    private final LSMTree lsmTree;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();
    
    public void put(String key, String value) {
        writeLock.lock();
        try {
            lsmTree.put(key, value);
        } finally {
            writeLock.unlock();
        }
    }
    
    public String get(String key) {
        readLock.lock();
        try {
            return lsmTree.get(key);
        } finally {
            readLock.unlock();
        }
    }
    
    public List<KeyValue> scan(String startKey, String endKey) {
        readLock.lock();
        try {
            return lsmTree.scan(startKey, endKey);
        } finally {
            readLock.unlock();
        }
    }
}
```

## 8.7 配置管理

### 配置类设计

```java
public class LSMTreeConfig {
    private int memTableThreshold = 1000;
    private int maxLevels = 7;
    private double bloomFilterFalsePositiveRate = 0.01;
    private String compressionType = "none";
    private int compactionThreads = 2;
    private boolean enableWAL = true;
    private long walSyncInterval = 1000; // ms
    
    public static LSMTreeConfig fromFile(String configPath) {
        try {
            Properties props = new Properties();
            props.load(new FileInputStream(configPath));
            
            LSMTreeConfig config = new LSMTreeConfig();
            config.memTableThreshold = Integer.parseInt(props.getProperty("memtable.threshold", "1000"));
            config.maxLevels = Integer.parseInt(props.getProperty("max.levels", "7"));
            config.bloomFilterFalsePositiveRate = Double.parseDouble(props.getProperty("bloom.filter.fpr", "0.01"));
            config.compressionType = props.getProperty("compression.type", "none");
            config.compactionThreads = Integer.parseInt(props.getProperty("compaction.threads", "2"));
            config.enableWAL = Boolean.parseBoolean(props.getProperty("wal.enabled", "true"));
            config.walSyncInterval = Long.parseLong(props.getProperty("wal.sync.interval", "1000"));
            
            return config;
        } catch (Exception e) {
            System.err.println("加载配置失败，使用默认配置: " + e.getMessage());
            return new LSMTreeConfig();
        }
    }
}
```

## 8.8 关闭与资源清理

### 优雅关闭

```java
public void close() {
    try {
        // 1. 停止后台任务
        if (compactionScheduler != null) {
            compactionScheduler.shutdown();
        }
        
        // 2. Flush最后的MemTable
        if (!memTable.isEmpty()) {
            flushMemTable();
        }
        
        // 3. 同步WAL
        wal.sync();
        
        // 4. 关闭所有资源
        wal.close();
        for (SSTable sstable : sstables) {
            sstable.close();
        }
        
        System.out.println("LSM Tree已安全关闭");
        
    } catch (Exception e) {
        System.err.println("关闭过程中出现错误: " + e.getMessage());
    }
}

// JVM关闭钩子
static {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        // 确保所有LSM Tree实例都被正确关闭
        LSMTreeRegistry.closeAll();
    }));
}
```

## 8.9 统计信息与监控

### 性能指标收集

```java
public class LSMTreeMetrics {
    private final AtomicLong readCount = new AtomicLong(0);
    private final AtomicLong writeCount = new AtomicLong(0);
    private final AtomicLong memTableHits = new AtomicLong(0);
    private final AtomicLong sstableHits = new AtomicLong(0);
    private final AtomicLong compactionCount = new AtomicLong(0);
    
    public void recordRead(boolean fromMemTable) {
        readCount.incrementAndGet();
        if (fromMemTable) {
            memTableHits.incrementAndGet();
        } else {
            sstableHits.incrementAndGet();
        }
    }
    
    public void recordWrite() {
        writeCount.incrementAndGet();
    }
    
    public void recordCompaction() {
        compactionCount.incrementAndGet();
    }
    
    public Map<String, Long> getMetrics() {
        Map<String, Long> metrics = new HashMap<>();
        metrics.put("reads", readCount.get());
        metrics.put("writes", writeCount.get());
        metrics.put("memtable_hits", memTableHits.get());
        metrics.put("sstable_hits", sstableHits.get());
        metrics.put("compactions", compactionCount.get());
        metrics.put("hit_rate", readCount.get() > 0 ? 
            (memTableHits.get() * 100 / readCount.get()) : 0);
        return metrics;
    }
}
```

## 8.10 示例使用

### 基本使用示例

```java
public class LSMTreeExample {
    public static void main(String[] args) {
        // 创建LSM Tree实例
        LSMTree lsmTree = new LSMTree("/tmp/lsm_data");
        
        try {
            // 写入数据
            lsmTree.put("user:1", "alice");
            lsmTree.put("user:2", "bob");
            lsmTree.put("user:3", "charlie");
            
            // 读取数据
            String user1 = lsmTree.get("user:1");
            System.out.println("user:1 = " + user1);
            
            // 范围查询
            List<KeyValue> users = lsmTree.scan("user:1", "user:3");
            users.forEach(kv -> System.out.println(kv.getKey() + " = " + kv.getValue()));
            
            // 删除数据
            lsmTree.delete("user:2");
            
            // 验证删除
            String deletedUser = lsmTree.get("user:2");
            System.out.println("user:2 after delete = " + deletedUser); // null
            
        } finally {
            // 关闭资源
            lsmTree.close();
        }
    }
}
```

通过本章的学习，我们了解了如何将前面各个组件整合成一个完整的LSM Tree系统。主程序需要协调各组件的工作，处理并发访问，提供配置管理和监控功能，确保系统的稳定性和性能。 