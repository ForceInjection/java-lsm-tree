# 第11章：故障排查

## 常见问题诊断

### 1. 写入性能下降

**症状**: 写入操作延迟增加，吞吐量下降

**可能原因和解决方案**:

```java
public class WritePerformanceDiagnostic {
    
    public static void diagnoseWritePerformance(LSMTree lsmTree) {
        System.out.println("=== 写入性能诊断 ===");
        
        // 检查MemTable大小
        int memTableSize = lsmTree.getMemTable().size();
        int threshold = lsmTree.getMemTableSizeThreshold();
        
        if (memTableSize > threshold * 0.9) {
            System.out.println("问题: MemTable接近满载");
            System.out.println("解决方案: 增加MemTable大小或降低刷盘阈值");
        }
        
        // 检查WAL文件大小
        File walFile = new File(lsmTree.getDataDirectory() + "/wal.log");
        if (walFile.exists() && walFile.length() > 10 * 1024 * 1024) {
            System.out.println("问题: WAL文件过大");
            System.out.println("解决方案: 增加WAL检查点频率");
        }
        
        // 检查后台任务
        if (lsmTree.isCompactionRunning()) {
            System.out.println("信息: 后台压缩正在进行，可能影响写入性能");
        }
        
        // 检查磁盘空间
        checkDiskSpace(lsmTree.getDataDirectory());
    }
    
    private static void checkDiskSpace(String directory) {
        File dir = new File(directory);
        long freeSpace = dir.getFreeSpace();
        long totalSpace = dir.getTotalSpace();
        double usage = (double)(totalSpace - freeSpace) / totalSpace * 100;
        
        if (usage > 90) {
            System.out.println("严重: 磁盘空间不足 " + String.format("%.1f%%", usage));
            System.out.println("解决方案: 清理磁盘空间或扩容");
        }
    }
}
```

### 2. 读取性能问题

**症状**: 查询响应时间长，读取吞吐量低

```java
public class ReadPerformanceDiagnostic {
    
    public static void diagnoseReadPerformance(LSMTree lsmTree) {
        System.out.println("=== 读取性能诊断 ===");
        
        // 检查SSTable数量
        int sstableCount = lsmTree.getSSTables().size();
        if (sstableCount > 10) {
            System.out.println("问题: SSTable文件过多 (" + sstableCount + ")");
            System.out.println("影响: 读放大过大");
            System.out.println("解决方案: 手动触发压缩或调整压缩策略");
        }
        
        // 检查布隆过滤器效率
        checkBloomFilterEfficiency(lsmTree);
        
        // 检查缓存命中率（如果有缓存）
        if (lsmTree instanceof LSMTreeWithCache) {
            LSMTreeWithCache cachedTree = (LSMTreeWithCache) lsmTree;
            double hitRate = cachedTree.getCacheHitRate();
            
            if (hitRate < 0.8) {
                System.out.println("问题: 缓存命中率低 " + String.format("%.2f%%", hitRate * 100));
                System.out.println("解决方案: 增加缓存大小或优化数据访问模式");
            }
        }
    }
    
    private static void checkBloomFilterEfficiency(LSMTree lsmTree) {
        // 模拟测试布隆过滤器效率
        try {
            long startTime = System.nanoTime();
            
            // 测试不存在的键
            for (int i = 0; i < 100; i++) {
                lsmTree.get("non_existent_key_" + i);
            }
            
            long duration = System.nanoTime() - startTime;
            double avgTime = duration / 100.0 / 1_000_000; // ms
            
            if (avgTime > 1.0) {
                System.out.println("问题: 查询不存在键的时间过长 " + String.format("%.2fms", avgTime));
                System.out.println("可能原因: 布隆过滤器假阳性率过高");
            }
            
        } catch (Exception e) {
            System.out.println("布隆过滤器测试失败: " + e.getMessage());
        }
    }
}
```

### 3. 内存泄漏问题

```java
public class MemoryLeakDiagnostic {
    
    public static void checkMemoryLeaks() {
        System.out.println("=== 内存泄漏检查 ===");
        
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        System.out.printf("内存使用: 已用 %.2f MB / 总计 %.2f MB (%.1f%%)%n",
                usedMemory / (1024.0 * 1024.0),
                totalMemory / (1024.0 * 1024.0),
                (double) usedMemory / totalMemory * 100);
        
        // 建议垃圾回收
        long beforeGC = usedMemory;
        System.gc();
        
        try {
            Thread.sleep(1000); // 等待GC完成
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long afterGC = runtime.totalMemory() - runtime.freeMemory();
        long freed = beforeGC - afterGC;
        
        System.out.printf("GC后释放: %.2f MB%n", freed / (1024.0 * 1024.0));
        
        if (freed < beforeGC * 0.1) {
            System.out.println("警告: GC释放内存很少，可能存在内存泄漏");
            System.out.println("建议: 检查是否有未关闭的资源或循环引用");
        }
    }
}
```

### 4. 文件损坏诊断

```java
public class FileCorruptionDiagnostic {
    
    public static void checkFileIntegrity(String dataDirectory) {
        System.out.println("=== 文件完整性检查 ===");
        
        File dir = new File(dataDirectory);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".db"));
        
        if (files == null) {
            System.out.println("数据目录为空或不存在");
            return;
        }
        
        int corruptedFiles = 0;
        
        for (File file : files) {
            try {
                // 尝试加载每个SSTable文件
                SSTable.loadFromFile(file.getAbsolutePath());
                System.out.println("✓ " + file.getName() + " - 正常");
            } catch (Exception e) {
                System.out.println("✗ " + file.getName() + " - 损坏: " + e.getMessage());
                corruptedFiles++;
                
                // 尝试修复
                attemptFileRepair(file);
            }
        }
        
        if (corruptedFiles > 0) {
            System.out.printf("发现 %d 个损坏文件%n", corruptedFiles);
        } else {
            System.out.println("所有文件完整性正常");
        }
    }
    
    private static void attemptFileRepair(File corruptedFile) {
        String backupPath = corruptedFile.getAbsolutePath() + ".backup";
        String repairedPath = corruptedFile.getAbsolutePath() + ".repaired";
        
        try {
            List<String> validLines = new ArrayList<>();
            
            try (BufferedReader reader = new BufferedReader(new FileReader(corruptedFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (isValidSSTableLine(line)) {
                        validLines.add(line);
                    }
                }
            }
            
            // 写入修复的文件
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(repairedPath))) {
                for (String line : validLines) {
                    writer.write(line);
                    writer.newLine();
                }
            }
            
            System.out.println("  → 修复文件已保存: " + repairedPath);
            System.out.println("  → 请手动验证后替换原文件");
            
        } catch (IOException e) {
            System.out.println("  → 文件修复失败: " + e.getMessage());
        }
    }
    
    private static boolean isValidSSTableLine(String line) {
        // 简单验证SSTable行格式
        if (line.trim().isEmpty()) return false;
        
        try {
            // 检查是否是数字（条目数量行）
            Integer.parseInt(line.trim());
            return true;
        } catch (NumberFormatException e) {
            // 检查是否是键值对行
            String[] parts = line.split("\\|");
            return parts.length == 4;
        }
    }
}
```

## 性能监控工具

### 1. 实时性能监控

```java
public class RealTimeMonitor {
    private final LSMTree lsmTree;
    private final Map<String, Double> metrics = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    public RealTimeMonitor(LSMTree lsmTree) {
        this.lsmTree = lsmTree;
    }
    
    public void startMonitoring() {
        scheduler.scheduleWithFixedDelay(this::collectMetrics, 0, 5, TimeUnit.SECONDS);
    }
    
    private void collectMetrics() {
        try {
            // 收集写入延迟
            long writeStart = System.nanoTime();
            lsmTree.put("__monitor_key__", "test");
            long writeLatency = System.nanoTime() - writeStart;
            metrics.put("write_latency_ms", writeLatency / 1_000_000.0);
            
            // 收集读取延迟
            long readStart = System.nanoTime();
            lsmTree.get("__monitor_key__");
            long readLatency = System.nanoTime() - readStart;
            metrics.put("read_latency_ms", readLatency / 1_000_000.0);
            
            // 收集系统指标
            Runtime runtime = Runtime.getRuntime();
            metrics.put("memory_usage_mb", (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0));
            metrics.put("sstable_count", (double) lsmTree.getSSTables().size());
            metrics.put("memtable_size", (double) lsmTree.getMemTable().size());
            
            // 输出关键指标
            if (metrics.get("write_latency_ms") > 100 || metrics.get("read_latency_ms") > 10) {
                System.out.printf("[警告] 延迟过高 - 写入: %.2fms, 读取: %.2fms%n",
                        metrics.get("write_latency_ms"), metrics.get("read_latency_ms"));
            }
            
        } catch (Exception e) {
            System.err.println("监控指标收集失败: " + e.getMessage());
        }
    }
    
    public Map<String, Double> getMetrics() {
        return new HashMap<>(metrics);
    }
    
    public void shutdown() {
        scheduler.shutdown();
    }
}
```

### 2. 日志分析工具

```java
public class LogAnalyzer {
    
    public static void analyzePerformanceLogs(String logFile) throws IOException {
        System.out.println("=== 性能日志分析 ===");
        
        Map<String, List<Double>> operationTimes = new HashMap<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                parseLogLine(line, operationTimes);
            }
        }
        
        // 分析统计
        for (Map.Entry<String, List<Double>> entry : operationTimes.entrySet()) {
            String operation = entry.getKey();
            List<Double> times = entry.getValue();
            
            if (!times.isEmpty()) {
                Collections.sort(times);
                
                double avg = times.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                double p50 = times.get(times.size() / 2);
                double p95 = times.get((int) (times.size() * 0.95));
                double p99 = times.get((int) (times.size() * 0.99));
                
                System.out.printf("%s - 平均: %.2fms, P50: %.2fms, P95: %.2fms, P99: %.2fms%n",
                        operation, avg, p50, p95, p99);
            }
        }
    }
    
    private static void parseLogLine(String line, Map<String, List<Double>> operationTimes) {
        // 解析格式: "2024-01-01 12:00:00 [INFO] PUT operation completed in 1.23ms"
        if (line.contains("operation completed in")) {
            try {
                String[] parts = line.split(" ");
                String operation = null;
                double time = 0.0;
                
                for (int i = 0; i < parts.length; i++) {
                    if (parts[i].equals("PUT") || parts[i].equals("GET") || parts[i].equals("DELETE")) {
                        operation = parts[i];
                    }
                    if (parts[i].endsWith("ms")) {
                        time = Double.parseDouble(parts[i].replace("ms", ""));
                        break;
                    }
                }
                
                if (operation != null && time > 0) {
                    operationTimes.computeIfAbsent(operation, k -> new ArrayList<>()).add(time);
                }
                
            } catch (Exception e) {
                // 忽略解析错误
            }
        }
    }
}
```

## 故障恢复流程

### 1. 系统启动失败

```java
public class SystemRecovery {
    
    public static LSMTree recoverFromFailure(String dataDirectory) {
        System.out.println("开始系统故障恢复...");
        
        try {
            // 步骤1: 检查数据目录
            File dir = new File(dataDirectory);
            if (!dir.exists()) {
                System.out.println("数据目录不存在，创建新的LSM Tree");
                return new LSMTree(dataDirectory);
            }
            
            // 步骤2: 检查WAL文件
            File walFile = new File(dataDirectory + "/wal.log");
            if (walFile.exists()) {
                System.out.println("发现WAL文件，开始恢复...");
                return recoverFromWAL(dataDirectory);
            }
            
            // 步骤3: 检查SSTable文件
            File[] sstableFiles = dir.listFiles((d, name) -> name.endsWith(".db"));
            if (sstableFiles != null && sstableFiles.length > 0) {
                System.out.println("发现SSTable文件，尝试恢复...");
                return recoverFromSSTables(dataDirectory);
            }
            
            // 步骤4: 创建新系统
            System.out.println("未发现有效数据，创建新的LSM Tree");
            return new LSMTree(dataDirectory);
            
        } catch (Exception e) {
            System.err.println("系统恢复失败: " + e.getMessage());
            e.printStackTrace();
            
            // 最后尝试：备份数据并重新开始
            return emergencyRecovery(dataDirectory);
        }
    }
    
    private static LSMTree recoverFromWAL(String dataDirectory) throws IOException {
        try {
            LSMTree lsmTree = new LSMTree(dataDirectory);
            System.out.println("WAL恢复成功");
            return lsmTree;
        } catch (Exception e) {
            System.err.println("WAL恢复失败: " + e.getMessage());
            
            // 尝试修复WAL文件
            repairWALFile(dataDirectory + "/wal.log");
            return new LSMTree(dataDirectory);
        }
    }
    
    private static LSMTree recoverFromSSTables(String dataDirectory) throws IOException {
        try {
            // 移除损坏的WAL文件
            File walFile = new File(dataDirectory + "/wal.log");
            if (walFile.exists()) {
                walFile.renameTo(new File(dataDirectory + "/wal.log.corrupted"));
            }
            
            LSMTree lsmTree = new LSMTree(dataDirectory);
            System.out.println("从SSTable恢复成功");
            return lsmTree;
            
        } catch (Exception e) {
            System.err.println("SSTable恢复失败: " + e.getMessage());
            throw e;
        }
    }
    
    private static LSMTree emergencyRecovery(String dataDirectory) {
        try {
            // 备份现有数据
            String backupDir = dataDirectory + "_backup_" + System.currentTimeMillis();
            File originalDir = new File(dataDirectory);
            File backup = new File(backupDir);
            
            if (originalDir.exists()) {
                Files.move(originalDir.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("原数据已备份到: " + backupDir);
            }
            
            // 创建新系统
            LSMTree lsmTree = new LSMTree(dataDirectory);
            System.out.println("紧急恢复完成，系统已重新初始化");
            
            return lsmTree;
            
        } catch (Exception e) {
            System.err.println("紧急恢复失败: " + e.getMessage());
            throw new RuntimeException("系统无法恢复", e);
        }
    }
    
    private static void repairWALFile(String walPath) {
        try {
            String repairedPath = walPath + ".repaired";
            
            try (BufferedReader reader = new BufferedReader(new FileReader(walPath));
                 BufferedWriter writer = new BufferedWriter(new FileWriter(repairedPath))) {
                
                String line;
                while ((line = reader.readLine()) != null) {
                    if (isValidWALLine(line)) {
                        writer.write(line);
                        writer.newLine();
                    }
                }
            }
            
            // 替换原文件
            File original = new File(walPath);
            File repaired = new File(repairedPath);
            
            original.renameTo(new File(walPath + ".corrupted"));
            repaired.renameTo(original);
            
            System.out.println("WAL文件修复完成");
            
        } catch (IOException e) {
            System.err.println("WAL文件修复失败: " + e.getMessage());
        }
    }
    
    private static boolean isValidWALLine(String line) {
        if (line.trim().isEmpty()) return false;
        
        String[] parts = line.split("\\|");
        if (parts.length != 4) return false;
        
        String operation = parts[0];
        return "put".equals(operation) || "delete".equals(operation);
    }
}
```

## 故障排查工具

```java
public class LSMTreeDiagnosticTool {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("用法: java LSMTreeDiagnosticTool <data_directory> [command]");
            System.out.println("命令:");
            System.out.println("  check - 检查系统健康状态");
            System.out.println("  repair - 修复损坏的文件");
            System.out.println("  stats - 显示详细统计信息");
            System.out.println("  monitor - 启动实时监控");
            return;
        }
        
        String dataDirectory = args[0];
        String command = args.length > 1 ? args[1] : "check";
        
        switch (command) {
            case "check":
                performHealthCheck(dataDirectory);
                break;
            case "repair":
                performRepair(dataDirectory);
                break;
            case "stats":
                showDetailedStats(dataDirectory);
                break;
            case "monitor":
                startMonitoring(dataDirectory);
                break;
            default:
                System.out.println("未知命令: " + command);
        }
    }
    
    private static void performHealthCheck(String dataDirectory) throws IOException {
        System.out.println("=== LSM Tree 健康检查 ===");
        
        // 检查文件完整性
        FileCorruptionDiagnostic.checkFileIntegrity(dataDirectory);
        
        // 检查内存
        MemoryLeakDiagnostic.checkMemoryLeaks();
        
        // 尝试启动系统
        try {
            LSMTree lsmTree = new LSMTree(dataDirectory);
            
            // 执行基本操作测试
            lsmTree.put("health_check", "test");
            String value = lsmTree.get("health_check");
            
            if ("test".equals(value)) {
                System.out.println("✓ 基本操作测试通过");
            } else {
                System.out.println("✗ 基本操作测试失败");
            }
            
            lsmTree.close();
            System.out.println("✓ 系统健康检查完成");
            
        } catch (Exception e) {
            System.out.println("✗ 系统启动失败: " + e.getMessage());
        }
    }
    
    private static void performRepair(String dataDirectory) {
        System.out.println("=== 开始修复操作 ===");
        
        try {
            LSMTree recovered = SystemRecovery.recoverFromFailure(dataDirectory);
            recovered.close();
            System.out.println("✓ 修复操作完成");
        } catch (Exception e) {
            System.out.println("✗ 修复操作失败: " + e.getMessage());
        }
    }
    
    private static void showDetailedStats(String dataDirectory) throws IOException {
        try (LSMTree lsmTree = new LSMTree(dataDirectory)) {
            LSMTreeStats stats = new LSMTreeStats(lsmTree);
            System.out.println(stats.getDetailedStats());
        }
    }
    
    private static void startMonitoring(String dataDirectory) throws IOException, InterruptedException {
        LSMTree lsmTree = new LSMTree(dataDirectory);
        RealTimeMonitor monitor = new RealTimeMonitor(lsmTree);
        
        monitor.startMonitoring();
        
        System.out.println("实时监控已启动... (按 Ctrl+C 退出)");
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            monitor.shutdown();
            try {
                lsmTree.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));
        
        Thread.currentThread().join();
    }
}
```

## 小结

故障排查的关键步骤：

1. **问题识别**: 监控关键指标，及时发现异常
2. **根因分析**: 使用诊断工具定位问题根源
3. **快速恢复**: 优先恢复服务，再处理数据一致性
4. **预防措施**: 改进监控和备份机制

## 下一步学习

继续阅读：[第12章：扩展开发](12-advanced-extensions.md)

---

## 最佳实践

1. **定期备份**: 重要数据定期备份
2. **监控告警**: 设置关键指标阈值告警
3. **故障演练**: 定期进行故障恢复演练
4. **文档记录**: 记录故障处理过程和解决方案 