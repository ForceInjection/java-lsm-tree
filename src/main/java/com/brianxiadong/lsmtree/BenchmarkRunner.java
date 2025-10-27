package com.brianxiadong.lsmtree;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LSM Tree 性能基准测试工具
 * 
 * 提供全面的性能测试功能，包括：
 * - 顺序写入性能测试
 * - 随机写入性能测试
 * - 读取性能测试
 * - 混合工作负载测试
 * - 写入延迟测试
 * - MemTable 刷盘影响测试
 * - 并发性能测试
 * - 删除操作性能测试
 * - 内存使用监控
 * 
 * @author Brian Xia Dong
 * @version 2.0
 */
public class BenchmarkRunner {
    
    /**
     * 基准测试配置类
     * 统一管理所有测试参数，便于调整和维护
     */
    public static class BenchmarkConfig {
        // 基础测试参数
        public final int numOperations;
        public final int keySize;
        public final int valueSize;
        public final String dataDir;
        public final long randomSeed;
        
        // 并发测试参数
        public final int threadCount;
        public final int concurrentOperations;
        
        // 内存和性能参数
        public final int memTableSizeThreshold;
        public final boolean enableMemoryMonitoring;
        public final int warmupOperations;
        
        // 延迟测试参数
        public final int latencyTestOperations;
        public final boolean enableDetailedLatencyStats;
        
        public BenchmarkConfig() {
            this(100000, 16, 100, "./benchmark_data", System.currentTimeMillis());
        }
        
        public BenchmarkConfig(int numOperations, int keySize, int valueSize, String dataDir, long randomSeed) {
            this(numOperations, keySize, valueSize, dataDir, randomSeed, Runtime.getRuntime().availableProcessors());
        }
        
        public BenchmarkConfig(int numOperations, int keySize, int valueSize, String dataDir, long randomSeed, int threadCount) {
            this.numOperations = numOperations;
            this.keySize = keySize;
            this.valueSize = valueSize;
            this.dataDir = dataDir;
            this.randomSeed = randomSeed;
            
            // 并发配置
            this.threadCount = threadCount;
            this.concurrentOperations = numOperations / threadCount;
            
            // 性能参数
            this.memTableSizeThreshold = 1024 * 1024; // 1MB
            this.enableMemoryMonitoring = true;
            this.warmupOperations = Math.min(1000, numOperations / 10);
            
            // 延迟测试参数
            this.latencyTestOperations = Math.min(10000, numOperations / 10);
            this.enableDetailedLatencyStats = true;
        }
    }
    
    /**
     * 内存监控器
     * 用于监控测试过程中的内存使用情况
     */
    public static class MemoryMonitor {
        private final Runtime runtime = Runtime.getRuntime();
        private long initialMemory;
        private long peakMemory;
        private final List<Long> memorySnapshots = new ArrayList<>();
        
        public void start() {
            System.gc(); // 建议进行垃圾回收以获得更准确的基线
            initialMemory = getUsedMemory();
            peakMemory = initialMemory;
            memorySnapshots.clear();
        }
        
        public void snapshot() {
            long currentMemory = getUsedMemory();
            memorySnapshots.add(currentMemory);
            if (currentMemory > peakMemory) {
                peakMemory = currentMemory;
            }
        }
        
        public void printReport() {
            long finalMemory = getUsedMemory();
            System.out.println("\n=== 内存使用报告 ===");
            System.out.printf("初始内存: %.2f MB%n", initialMemory / 1024.0 / 1024.0);
            System.out.printf("峰值内存: %.2f MB%n", peakMemory / 1024.0 / 1024.0);
            System.out.printf("最终内存: %.2f MB%n", finalMemory / 1024.0 / 1024.0);
            System.out.printf("内存增长: %.2f MB%n", (finalMemory - initialMemory) / 1024.0 / 1024.0);
            
            if (!memorySnapshots.isEmpty()) {
                double avgMemory = memorySnapshots.stream().mapToLong(Long::longValue).average().orElse(0);
                System.out.printf("平均内存: %.2f MB%n", avgMemory / 1024.0 / 1024.0);
            }
        }
        
        private long getUsedMemory() {
            return runtime.totalMemory() - runtime.freeMemory();
        }
    }
    
    /**
     * 性能统计收集器
     * 增强的统计信息收集和分析
     */
    public static class PerformanceStats {
        private final List<Long> latencies = new ArrayList<>();
        private final AtomicLong totalOperations = new AtomicLong(0);
        private final AtomicLong totalErrors = new AtomicLong(0);
        private long startTime;
        private long endTime;
        
        public void start() {
            startTime = System.nanoTime();
            latencies.clear();
            totalOperations.set(0);
            totalErrors.set(0);
        }
        
        public void recordOperation(long latencyNanos) {
            synchronized (latencies) {
                latencies.add(latencyNanos);
            }
            totalOperations.incrementAndGet();
        }
        
        public void recordError() {
            totalErrors.incrementAndGet();
        }
        
        public void end() {
            endTime = System.nanoTime();
        }
        
        public void printDetailedReport(String testName) {
            double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
            long ops = totalOperations.get();
            long errors = totalErrors.get();
            
            System.out.printf("\n=== %s 详细统计 ===%n", testName);
            System.out.printf("总操作数: %d%n", ops);
            System.out.printf("错误数: %d (%.2f%%)%n", errors, errors * 100.0 / (ops + errors));
            System.out.printf("总耗时: %.2f 秒%n", durationSeconds);
            System.out.printf("吞吐量: %.2f ops/sec%n", ops / durationSeconds);
            
            if (!latencies.isEmpty()) {
                Collections.sort(latencies);
                System.out.printf("平均延迟: %.2f ms%n", latencies.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0);
                System.out.printf("P50 延迟: %.2f ms%n", getPercentile(latencies, 50) / 1_000_000.0);
                System.out.printf("P95 延迟: %.2f ms%n", getPercentile(latencies, 95) / 1_000_000.0);
                System.out.printf("P99 延迟: %.2f ms%n", getPercentile(latencies, 99) / 1_000_000.0);
                System.out.printf("最大延迟: %.2f ms%n", latencies.get(latencies.size() - 1) / 1_000_000.0);
            }
        }
        
        private long getPercentile(List<Long> sortedList, int percentile) {
            int index = (int) Math.ceil(sortedList.size() * percentile / 100.0) - 1;
            return sortedList.get(Math.max(0, Math.min(index, sortedList.size() - 1)));
        }
    }
    
    private final BenchmarkConfig config;
    private final MemoryMonitor memoryMonitor;
    
    public BenchmarkRunner() {
        this(new BenchmarkConfig());
    }
    
    public BenchmarkRunner(BenchmarkConfig config) {
        this.config = config;
        this.memoryMonitor = new MemoryMonitor();
    }

    public static void main(String[] args) {
        try {
            BenchmarkConfig config = parseArgs(args);
            BenchmarkRunner runner = new BenchmarkRunner(config);
            runner.runAllBenchmarks();
        } catch (Exception e) {
            System.err.println("基准测试执行失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * 解析命令行参数
     */
    private static BenchmarkConfig parseArgs(String[] args) {
        if (args.length == 0) {
            return new BenchmarkConfig();
        }
        
        // 默认值
        int numOps = 100000;
        int keySize = 16;
        int valueSize = 100;
        String dataDir = "./benchmark_data";
        long seed = System.currentTimeMillis();
        int threads = Runtime.getRuntime().availableProcessors();
        
        // 解析命名参数
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            try {
                if ("--operations".equals(arg) && i + 1 < args.length) {
                    numOps = Integer.parseInt(args[++i]);
                } else if ("--key-size".equals(arg) && i + 1 < args.length) {
                    keySize = Integer.parseInt(args[++i]);
                } else if ("--value-size".equals(arg) && i + 1 < args.length) {
                    valueSize = Integer.parseInt(args[++i]);
                } else if ("--data-dir".equals(arg) && i + 1 < args.length) {
                    dataDir = args[++i];
                } else if ("--seed".equals(arg) && i + 1 < args.length) {
                    seed = Long.parseLong(args[++i]);
                } else if ("--threads".equals(arg) && i + 1 < args.length) {
                    threads = Integer.parseInt(args[++i]);
                } else if ("--help".equals(arg) || "-h".equals(arg)) {
                    printUsage();
                    System.exit(0);
                } else if (arg.startsWith("--")) {
                    System.err.println("未知参数: " + arg);
                    printUsage();
                    System.exit(1);
                } else if (i == 0) {
                    // 第一个参数如果不是命名参数，则作为操作数
                    numOps = Integer.parseInt(arg);
                }
            } catch (NumberFormatException e) {
                System.err.println("参数格式错误: " + arg + " = " + (i + 1 < args.length ? args[i + 1] : ""));
                printUsage();
                System.exit(1);
            }
        }
        
        return new BenchmarkConfig(numOps, keySize, valueSize, dataDir, seed, threads);
    }
    
    /**
     * 打印使用说明
     */
    private static void printUsage() {
        System.out.println("LSM Tree 基准测试工具");
        System.out.println("用法: java BenchmarkRunner [选项]");
        System.out.println("选项:");
        System.out.println("  --operations <数量>    操作数量 (默认: 100000)");
        System.out.println("  --key-size <字节>      键大小 (默认: 16)");
        System.out.println("  --value-size <字节>    值大小 (默认: 100)");
        System.out.println("  --data-dir <路径>      数据目录 (默认: ./benchmark_data)");
        System.out.println("  --seed <数字>          随机种子 (默认: 当前时间)");
        System.out.println("  --threads <数量>       线程数 (默认: CPU核心数)");
        System.out.println("  --help, -h             显示此帮助信息");
        System.out.println();
        System.out.println("示例:");
        System.out.println("  java BenchmarkRunner --operations 50000 --threads 4");
        System.out.println("  java BenchmarkRunner 10000  # 位置参数方式");
    }

    /**
     * 运行所有基准测试
     */
    public void runAllBenchmarks() {
        System.out.println("开始 LSM Tree 性能基准测试...");
        System.out.printf("配置: 操作数=%d, 键大小=%d, 值大小=%d, 线程数=%d%n", 
                         config.numOperations, config.keySize, config.valueSize, config.threadCount);
        
        if (config.enableMemoryMonitoring) {
            memoryMonitor.start();
        }
        
        try {
            // 基础性能测试
            benchmarkSequentialWrites();
            benchmarkRandomWrites();
            benchmarkReads();
            benchmarkMixedWorkload();
            
            // 高级性能测试
            benchmarkWriteLatency();
            benchmarkMemTableFlushImpact();
            
            // 新增测试
            benchmarkConcurrentOperations();
            benchmarkDeleteOperations();
            benchmarkRangeQueries();
            
        } catch (Exception e) {
            System.err.println("基准测试过程中发生错误: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (config.enableMemoryMonitoring) {
                memoryMonitor.printReport();
            }
            System.out.println("\n所有基准测试完成!");
        }
    }

    /**
     * 顺序写入性能测试
     */
    private void benchmarkSequentialWrites() {
        System.out.println("\n=== 顺序写入性能测试 ===");
        
        LSMTree lsmTree = null;
        PerformanceStats stats = new PerformanceStats();
        
        try {
            lsmTree = createLSMTree("sequential_writes");
            stats.start();
            
            // 预热
            performWarmup(lsmTree, "写入");
            
            Random random = new Random(config.randomSeed);
            
            for (int i = 0; i < config.numOperations; i++) {
                String key = String.format("key_%08d", i);
                String value = generateRandomValue(random, config.valueSize);
                
                long startTime = System.nanoTime();
                try {
                    lsmTree.put(key, value);
                    long endTime = System.nanoTime();
                    stats.recordOperation(endTime - startTime);
                } catch (Exception e) {
                    stats.recordError();
                    System.err.printf("写入操作失败 (key=%s): %s%n", key, e.getMessage());
                }
                
                if (config.enableMemoryMonitoring && i % 10000 == 0) {
                    memoryMonitor.snapshot();
                }
            }
            
            stats.end();
            stats.printDetailedReport("顺序写入");
            
            // 打印 LSM Tree 统计信息
            printLSMTreeStats(lsmTree, "顺序写入后");
            
        } catch (Exception e) {
            System.err.println("顺序写入测试失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeLSMTree(lsmTree);
        }
    }

    /**
     * 随机写入性能测试
     */
    private void benchmarkRandomWrites() {
        System.out.println("\n=== 随机写入性能测试 ===");
        
        LSMTree lsmTree = null;
        PerformanceStats stats = new PerformanceStats();
        
        try {
            lsmTree = createLSMTree("random_writes");
            stats.start();
            
            // 预热
            performWarmup(lsmTree, "写入");
            
            Random random = new Random(config.randomSeed);
            
            for (int i = 0; i < config.numOperations; i++) {
                String key = String.format("key_%08d", random.nextInt(config.numOperations * 2));
                String value = generateRandomValue(random, config.valueSize);
                
                long startTime = System.nanoTime();
                try {
                    lsmTree.put(key, value);
                    long endTime = System.nanoTime();
                    stats.recordOperation(endTime - startTime);
                } catch (Exception e) {
                    stats.recordError();
                    System.err.printf("随机写入操作失败 (key=%s): %s%n", key, e.getMessage());
                }
                
                if (config.enableMemoryMonitoring && i % 10000 == 0) {
                    memoryMonitor.snapshot();
                }
            }
            
            stats.end();
            stats.printDetailedReport("随机写入");
            
            printLSMTreeStats(lsmTree, "随机写入后");
            
        } catch (Exception e) {
            System.err.println("随机写入测试失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeLSMTree(lsmTree);
        }
    }

    /**
     * 读取性能测试
     */
    private void benchmarkReads() {
        System.out.println("\n=== 读取性能测试 ===");
        
        LSMTree lsmTree = null;
        PerformanceStats stats = new PerformanceStats();
        
        try {
            lsmTree = createLSMTree("reads");
            
            // 首先写入测试数据
            System.out.println("准备测试数据...");
            Random random = new Random(config.randomSeed);
            List<String> testKeys = new ArrayList<>();
            
            for (int i = 0; i < config.numOperations; i++) {
                String key = String.format("key_%08d", i);
                String value = generateRandomValue(random, config.valueSize);
                testKeys.add(key);
                lsmTree.put(key, value);
            }
            
            // 预热
            performWarmup(lsmTree, "读取");
            
            // 开始读取测试
            stats.start();
            Collections.shuffle(testKeys, random); // 随机化读取顺序
            
            for (String key : testKeys) {
                long startTime = System.nanoTime();
                try {
                    String value = lsmTree.get(key);
                    long endTime = System.nanoTime();
                    stats.recordOperation(endTime - startTime);
                    
                    if (value == null) {
                        System.err.printf("警告: 键 %s 未找到%n", key);
                    }
                } catch (Exception e) {
                    stats.recordError();
                    System.err.printf("读取操作失败 (key=%s): %s%n", key, e.getMessage());
                }
            }
            
            stats.end();
            stats.printDetailedReport("读取");
            
            printLSMTreeStats(lsmTree, "读取测试后");
            
        } catch (Exception e) {
            System.err.println("读取测试失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeLSMTree(lsmTree);
        }
    }

    /**
     * 混合工作负载测试
     */
    private void benchmarkMixedWorkload() {
        System.out.println("\n=== 混合工作负载测试 ===");
        
        LSMTree lsmTree = null;
        PerformanceStats writeStats = new PerformanceStats();
        PerformanceStats readStats = new PerformanceStats();
        
        try {
            lsmTree = createLSMTree("mixed_workload");
            
            Random random = new Random(config.randomSeed);
            List<String> existingKeys = new ArrayList<>();
            
            // 预先写入一些数据
            for (int i = 0; i < config.numOperations / 2; i++) {
                String key = String.format("key_%08d", i);
                String value = generateRandomValue(random, config.valueSize);
                existingKeys.add(key);
                lsmTree.put(key, value);
            }
            
            writeStats.start();
            readStats.start();
            
            // 混合操作：70% 读取，30% 写入
            for (int i = 0; i < config.numOperations; i++) {
                if (random.nextDouble() < 0.7 && !existingKeys.isEmpty()) {
                    // 读取操作
                    String key = existingKeys.get(random.nextInt(existingKeys.size()));
                    long startTime = System.nanoTime();
                    try {
                        lsmTree.get(key);
                        long endTime = System.nanoTime();
                        readStats.recordOperation(endTime - startTime);
                    } catch (Exception e) {
                        readStats.recordError();
                        System.err.printf("混合工作负载读取失败 (key=%s): %s%n", key, e.getMessage());
                    }
                } else {
                    // 写入操作
                    String key = String.format("key_%08d", config.numOperations / 2 + i);
                    String value = generateRandomValue(random, config.valueSize);
                    existingKeys.add(key);
                    
                    long startTime = System.nanoTime();
                    try {
                        lsmTree.put(key, value);
                        long endTime = System.nanoTime();
                        writeStats.recordOperation(endTime - startTime);
                    } catch (Exception e) {
                        writeStats.recordError();
                        System.err.printf("混合工作负载写入失败 (key=%s): %s%n", key, e.getMessage());
                    }
                }
                
                if (config.enableMemoryMonitoring && i % 10000 == 0) {
                    memoryMonitor.snapshot();
                }
            }
            
            writeStats.end();
            readStats.end();
            
            writeStats.printDetailedReport("混合工作负载 - 写入");
            readStats.printDetailedReport("混合工作负载 - 读取");
            
            printLSMTreeStats(lsmTree, "混合工作负载后");
            
        } catch (Exception e) {
            System.err.println("混合工作负载测试失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeLSMTree(lsmTree);
        }
    }

    /**
     * 写入延迟测试
     */
    private void benchmarkWriteLatency() {
        System.out.println("\n=== 写入延迟详细分析 ===");
        
        LSMTree lsmTree = null;
        
        try {
            lsmTree = createLSMTree("write_latency");
            Random random = new Random(config.randomSeed);
            
            List<Long> latencies = new ArrayList<>();
            
            for (int i = 0; i < config.latencyTestOperations; i++) {
                String key = String.format("latency_key_%08d", i);
                String value = generateRandomValue(random, config.valueSize);
                
                long startTime = System.nanoTime();
                try {
                    lsmTree.put(key, value);
                    long endTime = System.nanoTime();
                    latencies.add(endTime - startTime);
                } catch (Exception e) {
                    System.err.printf("延迟测试写入失败 (key=%s): %s%n", key, e.getMessage());
                }
            }
            
            // 延迟统计分析
            Collections.sort(latencies);
            
            System.out.printf("延迟统计 (基于 %d 次操作):%n", latencies.size());
            System.out.printf("平均延迟: %.2f μs%n", latencies.stream().mapToLong(Long::longValue).average().orElse(0) / 1000.0);
            System.out.printf("中位数延迟: %.2f μs%n", getPercentile(latencies, 50) / 1000.0);
            System.out.printf("P90 延迟: %.2f μs%n", getPercentile(latencies, 90) / 1000.0);
            System.out.printf("P95 延迟: %.2f μs%n", getPercentile(latencies, 95) / 1000.0);
            System.out.printf("P99 延迟: %.2f μs%n", getPercentile(latencies, 99) / 1000.0);
            System.out.printf("P99.9 延迟: %.2f μs%n", getPercentile(latencies, 99.9) / 1000.0);
            System.out.printf("最大延迟: %.2f μs%n", latencies.get(latencies.size() - 1) / 1000.0);
            
            printLSMTreeStats(lsmTree, "延迟测试后");
            
        } catch (Exception e) {
            System.err.println("写入延迟测试失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeLSMTree(lsmTree);
        }
    }

    /**
     * MemTable 刷盘影响测试
     */
    private void benchmarkMemTableFlushImpact() {
        System.out.println("\n=== MemTable 刷盘影响测试 ===");
        
        LSMTree lsmTree = null;
        
        try {
            lsmTree = createLSMTree("memtable_flush");
            Random random = new Random(config.randomSeed);
            
            List<Long> normalLatencies = new ArrayList<>();
            List<Long> flushLatencies = new ArrayList<>();
            
            // 频繁刷盘场景下的性能测试
            for (int i = 0; i < config.numOperations; i++) {
                String key = String.format("flush_key_%08d", i);
                String value = generateRandomValue(random, config.valueSize);
                
                long startTime = System.nanoTime();
                try {
                    lsmTree.put(key, value);
                    long endTime = System.nanoTime();
                    long latency = endTime - startTime;
                    
                    // 简单判断是否可能触发了刷盘（延迟异常高）
                    if (latency > 10_000_000) { // 10ms 以上认为可能是刷盘
                        flushLatencies.add(latency);
                    } else {
                        normalLatencies.add(latency);
                    }
                } catch (Exception e) {
                    System.err.printf("刷盘测试写入失败 (key=%s): %s%n", key, e.getMessage());
                }
                
                // 每 1000 次操作强制刷盘一次
                if (i % 1000 == 0 && i > 0) {
                    try {
                        lsmTree.flush();
                    } catch (Exception e) {
                        System.err.printf("强制刷盘失败: %s%n", e.getMessage());
                    }
                }
            }
            
            System.out.printf("正常写入操作数: %d%n", normalLatencies.size());
            System.out.printf("疑似刷盘操作数: %d%n", flushLatencies.size());
            
            if (!normalLatencies.isEmpty()) {
                Collections.sort(normalLatencies);
                System.out.printf("正常写入平均延迟: %.2f μs%n", 
                    normalLatencies.stream().mapToLong(Long::longValue).average().orElse(0) / 1000.0);
            }
            
            if (!flushLatencies.isEmpty()) {
                Collections.sort(flushLatencies);
                System.out.printf("刷盘操作平均延迟: %.2f ms%n", 
                    flushLatencies.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0);
            }
            
            printLSMTreeStats(lsmTree, "刷盘测试后");
            
        } catch (Exception e) {
            System.err.println("MemTable 刷盘测试失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeLSMTree(lsmTree);
        }
    }
    
    /**
     * 并发操作性能测试
     */
    private void benchmarkConcurrentOperations() {
        System.out.println("\n=== 并发操作性能测试 ===");
        
        LSMTree lsmTree = null;
        
        try {
            lsmTree = createLSMTree("concurrent_ops");
            final LSMTree finalLsmTree = lsmTree; // 创建final引用供lambda使用
            ExecutorService executor = Executors.newFixedThreadPool(config.threadCount);
            CountDownLatch latch = new CountDownLatch(config.threadCount);
            
            AtomicLong totalOperations = new AtomicLong(0);
            AtomicLong totalErrors = new AtomicLong(0);
            long startTime = System.nanoTime();
            
            // 启动多个线程进行并发写入
            for (int t = 0; t < config.threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        Random random = new Random(config.randomSeed + threadId);
                        
                        for (int i = 0; i < config.concurrentOperations; i++) {
                            String key = String.format("concurrent_%d_%08d", threadId, i);
                            String value = generateRandomValue(random, config.valueSize);
                            
                            try {
                                finalLsmTree.put(key, value);
                                totalOperations.incrementAndGet();
                            } catch (Exception e) {
                                totalErrors.incrementAndGet();
                                System.err.printf("并发写入失败 (thread=%d, key=%s): %s%n", 
                                                threadId, key, e.getMessage());
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await();
            long endTime = System.nanoTime();
            executor.shutdown();
            
            double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
            long ops = totalOperations.get();
            long errors = totalErrors.get();
            
            System.out.printf("并发测试结果:%n");
            System.out.printf("线程数: %d%n", config.threadCount);
            System.out.printf("总操作数: %d%n", ops);
            System.out.printf("错误数: %d (%.2f%%)%n", errors, errors * 100.0 / (ops + errors));
            System.out.printf("总耗时: %.2f 秒%n", durationSeconds);
            System.out.printf("并发吞吐量: %.2f ops/sec%n", ops / durationSeconds);
            
            printLSMTreeStats(lsmTree, "并发测试后");
            
        } catch (Exception e) {
            System.err.println("并发操作测试失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeLSMTree(lsmTree);
        }
    }
    
    /**
     * 删除操作性能测试
     */
    private void benchmarkDeleteOperations() {
        System.out.println("\n=== 删除操作性能测试 ===");
        
        LSMTree lsmTree = null;
        PerformanceStats stats = new PerformanceStats();
        
        try {
            lsmTree = createLSMTree("delete_ops");
            Random random = new Random(config.randomSeed);
            List<String> keysToDelete = new ArrayList<>();
            
            // 首先写入数据
            System.out.println("准备删除测试数据...");
            for (int i = 0; i < config.numOperations; i++) {
                String key = String.format("delete_key_%08d", i);
                String value = generateRandomValue(random, config.valueSize);
                keysToDelete.add(key);
                lsmTree.put(key, value);
            }
            
            // 开始删除测试
            stats.start();
            Collections.shuffle(keysToDelete, random);
            
            for (String key : keysToDelete) {
                long startTime = System.nanoTime();
                try {
                    lsmTree.delete(key);
                    long endTime = System.nanoTime();
                    stats.recordOperation(endTime - startTime);
                } catch (Exception e) {
                    stats.recordError();
                    System.err.printf("删除操作失败 (key=%s): %s%n", key, e.getMessage());
                }
            }
            
            stats.end();
            stats.printDetailedReport("删除操作");
            
            // 验证删除效果
            System.out.println("验证删除效果...");
            int foundCount = 0;
            for (String key : keysToDelete.subList(0, Math.min(1000, keysToDelete.size()))) {
                try {
                    if (lsmTree.get(key) != null) {
                        foundCount++;
                    }
                } catch (Exception e) {
                    System.err.printf("验证删除时读取失败 (key=%s): %s%n", key, e.getMessage());
                }
            }
            System.out.printf("删除验证: 在 %d 个样本中发现 %d 个未删除的键%n", 
                            Math.min(1000, keysToDelete.size()), foundCount);
            
            printLSMTreeStats(lsmTree, "删除测试后");
            
        } catch (Exception e) {
            System.err.println("删除操作测试失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeLSMTree(lsmTree);
        }
    }
    
    /**
     * 范围查询性能测试
     */
    private void benchmarkRangeQueries() {
        System.out.println("\n=== 范围查询性能测试 ===");
        
        LSMTree lsmTree = null;
        PerformanceStats stats = new PerformanceStats();
        
        try {
            lsmTree = createLSMTree("range_queries");
            Random random = new Random(config.randomSeed);
            
            // 写入有序数据以便进行范围查询
            System.out.println("准备范围查询测试数据...");
            for (int i = 0; i < config.numOperations; i++) {
                String key = String.format("range_key_%08d", i);
                String value = generateRandomValue(random, config.valueSize);
                lsmTree.put(key, value);
            }
            
            // 模拟范围查询（由于当前 LSMTree 可能不支持范围查询，这里模拟连续键的查询）
            stats.start();
            int rangeSize = Math.min(100, config.numOperations); // 每次查询最多 100 个连续键
            int numRangeQueries = Math.max(1, config.numOperations / rangeSize);
            int maxStartIdx = Math.max(1, config.numOperations - rangeSize);
            
            for (int i = 0; i < numRangeQueries; i++) {
                int startIdx = random.nextInt(maxStartIdx);
                long startTime = System.nanoTime();
                
                try {
                    int foundCount = 0;
                    for (int j = 0; j < rangeSize; j++) {
                        String key = String.format("range_key_%08d", startIdx + j);
                        if (lsmTree.get(key) != null) {
                            foundCount++;
                        }
                    }
                    
                    long endTime = System.nanoTime();
                    stats.recordOperation(endTime - startTime);
                    
                    if (foundCount != rangeSize) {
                        System.err.printf("范围查询不完整: 期望 %d，实际找到 %d%n", rangeSize, foundCount);
                    }
                } catch (Exception e) {
                    stats.recordError();
                    System.err.printf("范围查询失败 (起始索引=%d): %s%n", startIdx, e.getMessage());
                }
            }
            
            stats.end();
            stats.printDetailedReport("范围查询");
            
            printLSMTreeStats(lsmTree, "范围查询测试后");
            
        } catch (Exception e) {
            System.err.println("范围查询测试失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeLSMTree(lsmTree);
        }
    }
    
    // 辅助方法
    
    /**
     * 创建 LSM Tree 实例
     */
    private LSMTree createLSMTree(String testName) throws IOException {
        String dataDir = config.dataDir + "/" + testName;
        File dir = new File(dataDir);
        if (dir.exists()) {
            deleteDirectory(dir);
        }
        dir.mkdirs();
        
        return new LSMTree(dataDir, config.memTableSizeThreshold);
    }
    
    /**
     * 安全关闭 LSM Tree
     */
    private void closeLSMTree(LSMTree lsmTree) {
        if (lsmTree != null) {
            try {
                lsmTree.close();
            } catch (Exception e) {
                System.err.println("关闭 LSM Tree 时发生错误: " + e.getMessage());
            }
        }
    }
    
    /**
     * 执行预热操作
     */
    private void performWarmup(LSMTree lsmTree, String operationType) {
        if (config.warmupOperations <= 0) return;
        
        System.out.printf("执行 %s 预热操作 (%d 次)...%n", operationType, config.warmupOperations);
        Random random = new Random(config.randomSeed);
        
        try {
            for (int i = 0; i < config.warmupOperations; i++) {
                String key = String.format("warmup_%s_%d", operationType, i);
                String value = generateRandomValue(random, config.valueSize);
                
                if ("读取".equals(operationType)) {
                    lsmTree.get(key); // 可能返回 null，这是正常的
                } else {
                    lsmTree.put(key, value);
                }
            }
        } catch (Exception e) {
            System.err.printf("预热操作失败: %s%n", e.getMessage());
        }
    }
    
    /**
     * 打印 LSM Tree 统计信息
     */
    private void printLSMTreeStats(LSMTree lsmTree, String phase) {
        try {
            LSMTree.LSMTreeStats stats = lsmTree.getStats();
            System.out.printf("\n--- %s LSM Tree 统计 ---%n", phase);
            System.out.printf("活跃 MemTable 大小: %d%n", stats.getActiveMemTableSize());
            System.out.printf("不可变 MemTable 数量: %d%n", stats.getImmutableMemTableCount());
            System.out.printf("SSTable 数量: %d%n", stats.getSsTableCount());
        } catch (Exception e) {
            System.err.printf("获取 LSM Tree 统计信息失败: %s%n", e.getMessage());
        }
    }
    
    /**
     * 生成随机值
     */
    private String generateRandomValue(Random random, int size) {
        StringBuilder sb = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            sb.append((char) ('a' + random.nextInt(26)));
        }
        return sb.toString();
    }
    
    /**
     * 计算百分位数
     */
    private long getPercentile(List<Long> sortedList, double percentile) {
        int index = (int) Math.ceil(sortedList.size() * percentile / 100.0) - 1;
        return sortedList.get(Math.max(0, Math.min(index, sortedList.size() - 1)));
    }
    
    /**
     * 递归删除目录
     */
    private void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        dir.delete();
    }
}