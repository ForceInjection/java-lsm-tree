# T8 内存优化技术方案与指南

> **关联任务**：本文档是 [advanced-tasks.md](../learn/advanced-tasks.md) 中 **T8: 内存管理优化任务** 的核心参考文档。
>
> **前置学习**：建议先完成 [learning-plan.md](../learn/learning-plan.md) 中第 3 天（MemTable）和第 9 天（性能测试）的内容。

## 概述

T8内存优化模块为Java LSM Tree提供了完整的内存管理解决方案，包括对象池、堆外内存管理、GC优化和实时监控等功能。

## 核心组件

### 1. MemoryManager 内存管理器

```java
// 创建默认内存管理器
DefaultMemoryManager memoryManager = new DefaultMemoryManager();

// 启用内存优化
memoryManager.enableOptimization();

// 禁用内存优化
memoryManager.disableOptimization();
```

### 2. 对象池 (ObjectPool)

```java
// 获取特定类型的对象池
ObjectPool<StringBuilder> sbPool = memoryManager.getObjectPool(StringBuilder.class);
ObjectPool<KeyValue> kvPool = memoryManager.getObjectPool(KeyValue.class);

// 使用对象池
StringBuilder sb = sbPool.borrowObject();
try {
    sb.append("hello").append("world");
    String result = sb.toString();
    // 处理结果...
} finally {
    sbPool.returnObject(sb); // 记得归还对象
}
```

### 3. 堆外内存管理

```java
// 分配堆外内存
ByteBuffer directBuffer = memoryManager.allocate(8192, true); // 8KB直接缓冲区

// 分配堆内内存
ByteBuffer heapBuffer = memoryManager.allocate(4096, false); // 4KB堆内缓冲区

// 使用完后释放
memoryManager.deallocate(directBuffer);
```

### 4. 内存监控

```java
// 获取内存使用统计
MemoryUsageStats stats = memoryManager.getMemoryStats();
System.out.println("堆内存使用率: " + stats.getHeapUsageRatio() * 100 + "%");
System.out.println("堆外内存使用率: " + stats.getOffHeapUsageRatio() * 100 + "%");
System.out.println("GC次数: " + stats.getGcCount());
System.out.println("平均GC暂停时间: " + stats.getGcPauseAvg() + "ms");

// 打印详细内存信息
MemoryMonitor monitor = new MemoryMonitor();
monitor.printDetailedMemoryInfo();
```

## 优化的MemTable

```java
// 创建优化的MemTable
OptimizedMemTable optimizedTable = new OptimizedMemTable(10000, memoryManager);

// 使用优化的范围查询
List<String> values = optimizedTable.getRangeValues("key_start", "key_end", true, true);

// 批量插入优化
List<KeyValue> batch = Arrays.asList(
    new KeyValue("key1", "value1"),
    new KeyValue("key2", "value2")
);
optimizedTable.putBatch(batch);

// 获取优化统计信息
OptimizedMemTable.MemoryOptimizationStats optStats = optimizedTable.getOptimizationStats();
System.out.println("对象池统计: " + optStats.getKeyValuePoolStats());
System.out.println("内存使用统计: " + optStats.getMemoryStats());
```

## GC配置优化

```java
// 配置GC参数
GCConfig gcConfig = new GCConfig();
gcConfig.setHeapSizeMB(8192);           // 8GB堆内存
gcConfig.setMaxGCPauseMillis(100);      // 最大GC暂停100ms
gcConfig.setUseStringDeduplication(true); // 启用字符串去重
gcConfig.setG1HeapRegionSizeMB(32);     // G1区域大小32MB

// 应用配置
memoryManager.updateGCConfig(gcConfig);

// 获取JVM启动参数
String[] jvmArgs = gcConfig.toJvmArgs();
System.out.println("推荐的JVM参数: " + Arrays.toString(jvmArgs));
```

## 最佳实践

### 1. 对象池使用建议

```java
// ✅ 正确用法
ObjectPool<StringBuilder> pool = memoryManager.getObjectPool(StringBuilder.class);
StringBuilder sb = pool.borrowObject();
try {
    // 使用对象
    sb.append("data");
    String result = sb.toString();
} finally {
    // 务必在finally块中归还对象
    pool.returnObject(sb);
}

// ❌ 错误用法 - 忘记归还对象会导致内存泄漏
StringBuilder sb = pool.borrowObject();
sb.append("data");
// 缺少 pool.returnObject(sb);
```

### 2. 内存监控最佳实践

```java
// 定期监控内存使用
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
scheduler.scheduleAtFixedRate(() -> {
    MemoryUsageStats stats = memoryManager.getMemoryStats();

    // 监控内存使用率
    if (stats.getHeapUsageRatio() > 0.8) {
        System.out.println("警告: 堆内存使用率过高 " + (stats.getHeapUsageRatio() * 100) + "%");
    }

    // 监控GC频率
    if (stats.getGcCount() - lastGcCount > 10) {
        System.out.println("警告: GC频率过高");
    }

    lastGcCount = stats.getGcCount();
}, 0, 30, TimeUnit.SECONDS);
```

### 3. 大数据量场景优化

```java
// 大数据量插入优化
public void bulkInsertOptimized(List<KeyValue> data) {
    DefaultMemoryManager manager = new DefaultMemoryManager();
    manager.enableOptimization();

    OptimizedMemTable table = new OptimizedMemTable(50000, manager);

    // 分批处理避免内存峰值
    int batchSize = 1000;
    for (int i = 0; i < data.size(); i += batchSize) {
        int end = Math.min(i + batchSize, data.size());
        List<KeyValue> batch = data.subList(i, end);
        table.putBatch(batch);

        // 定期触发GC
        if (i % 10000 == 0) {
            System.gc();
        }
    }
}
```

### 4. 性能调优建议

```java
// 根据工作负载调整对象池大小
GenericObjectPool<StringBuilder> sbPool = new GenericObjectPool<>(
    StringBuilder.class,
    cls -> new StringBuilder(256) // 预设容量减少扩容开销
);

// 调整池参数
sbPool.setMaxSize(2000);  // 最大池大小
sbPool.setMinIdle(10);    // 最小空闲对象
sbPool.setMaxIdle(100);   // 最大空闲对象
```

## 性能基准测试

```java
// 运行集成测试
MemoryOptimizationIntegrationTest integrationTest = new MemoryOptimizationIntegrationTest();
// 执行各种测试方法...

// 运行大规模基准测试
LargeScaleMemoryBenchmark.main(new String[]{});

// 运行性能基准测试
MemoryOptimizationBenchmark.main(new String[]{});
```

## 故障排除

### 常见问题

1. **内存泄漏**
   - 确保所有borrowed对象都被正确returned
   - 使用try-finally确保对象归还
   - 定期检查PoolStats中的activeCount

2. **GC压力过大**
   - 调整堆外内存使用比例
   - 优化对象池大小配置
   - 启用字符串去重功能

3. **性能下降**
   - 检查对象池命中率是否过低
   - 调整预填充大小
   - 优化回收策略参数

### 监控指标

重点关注以下指标：

- 对象池命中率 > 80%
- 内存使用率 < 85%
- GC暂停时间 < 100ms
- GC频率 < 每分钟5次

## 配置参考

### 推荐的JVM参数

```bash
# G1GC优化配置
-XX:+UseG1GC
-Xmx8g -Xms8g
-XX:MaxGCPauseMillis=100
-XX:+UseStringDeduplication
-XX:G1HeapRegionSize=32m
-XX:+UseCompressedOops
```

### 对象池配置建议

```java
// 高频使用的对象池
GenericObjectPool<StringBuilder> highFreqPool = new GenericObjectPool<>(
    StringBuilder.class,
    cls -> new StringBuilder(512)
);
highFreqPool.setMaxSize(5000);
highFreqPool.setMinIdle(50);
highFreqPool.setMaxIdle(200);

// 低频使用的对象池
GenericObjectPool<KeyValue> lowFreqPool = new GenericObjectPool<>(
    KeyValue.class,
    cls -> new KeyValue("", "")
);
lowFreqPool.setMaxSize(1000);
lowFreqPool.setMinIdle(5);
lowFreqPool.setMaxIdle(50);
```

通过合理使用这些内存优化功能，可以显著减少GC压力，提高系统性能和稳定性。
