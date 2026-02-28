package com.brianxiadong.lsmtree.memory;

import com.brianxiadong.lsmtree.KeyValue;
import com.brianxiadong.lsmtree.MemTable;
import com.brianxiadong.lsmtree.LSMTree;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 内存优化性能基准测试
 * 对比优化前后的性能差异
 */
public class MemoryOptimizationBenchmark {
    
    private static final int WARMUP_ITERATIONS = 1000;
    private static final int TEST_ITERATIONS = 10000;
    private static final int BATCH_SIZE = 1000;
    
    public static void main(String[] args) throws IOException {
        System.out.println("=== T8 内存优化性能基准测试 ===\n");
        
        // 运行所有基准测试
        benchmarkMemTablePerformance();
        benchmarkGCPressure();
        benchmarkMemoryUsage();
        benchmarkLSMTreeIntegration();
    }
    
    /**
     * MemTable性能基准测试
     */
    private static void benchmarkMemTablePerformance() throws IOException {
        System.out.println("1. MemTable性能对比测试");
        System.out.println("------------------------");
        
        // 标准MemTable测试
        MemTable standardTable = new MemTable(10000);
        long standardTime = runMemTableBenchmark(standardTable, "标准MemTable");
        
        // 优化MemTable测试
        DefaultMemoryManager memoryManager = new DefaultMemoryManager();
        memoryManager.enableOptimization();
        OptimizedMemTable optimizedTable = new OptimizedMemTable(10000, memoryManager);
        long optimizedTime = runMemTableBenchmark(optimizedTable, "优化MemTable");
        
        // 性能对比
        double improvement = (double)(standardTime - optimizedTime) / standardTime * 100;
        System.out.printf("性能提升: %.2f%%\n", improvement);
        System.out.printf("标准实现: %.2f ms\n", standardTime / 1_000_000.0);
        System.out.printf("优化实现: %.2f ms\n", optimizedTime / 1_000_000.0);
        System.out.println();
    }
    
    /**
     * GC压力测试
     */
    private static void benchmarkGCPressure() {
        System.out.println("2. GC压力测试");
        System.out.println("-------------");
        
        DefaultMemoryManager memoryManager = new DefaultMemoryManager();
        MemoryUsageStats initialStats = memoryManager.getMemoryStats();
        
        // 记录初始GC统计
        long initialGCCount = initialStats.getGcCount();
        long initialGCTime = initialStats.getGcTime();
        
        System.out.printf("初始GC次数: %d, 初始GC时间: %d ms\n", initialGCCount, initialGCTime);
        
        // 执行大量操作
        MemTable table = new MemTable(5000);
        memoryManager.enableOptimization();
        
        for (int batch = 0; batch < 20; batch++) {
            for (int i = 0; i < 1000; i++) {
                table.put("gc_test_" + batch + "_" + i, "value_" + i);
            }
            
            // 定期触发GC
            if (batch % 5 == 0) {
                System.gc();
                try { Thread.sleep(100); } catch (InterruptedException e) {}
            }
        }
        
        // 检查最终统计
        MemoryUsageStats finalStats = memoryManager.getMemoryStats();
        long finalGCCount = finalStats.getGcCount();
        long finalGCTime = finalStats.getGcTime();
        
        long gcCountIncrease = finalGCCount - initialGCCount;
        long gcTimeIncrease = finalGCTime - initialGCTime;
        
        System.out.printf("最终GC次数: %d, 最终GC时间: %d ms\n", finalGCCount, finalGCTime);
        System.out.printf("GC次数增加: %d (%.1f次/批)\n", gcCountIncrease, (double)gcCountIncrease / 20);
        System.out.printf("GC时间增加: %d ms\n", gcTimeIncrease);
        System.out.println();
    }
    
    /**
     * 内存使用测试
     */
    private static void benchmarkMemoryUsage() {
        System.out.println("3. 内存使用测试");
        System.out.println("---------------");
        
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        
        long baselineMemory = runtime.totalMemory() - runtime.freeMemory();
        System.out.printf("基线内存使用: %.2f MB\n", baselineMemory / (1024.0 * 1024.0));
        
        // 测试标准实现
        MemTable standardTable = new MemTable(10000);
        populateTable(standardTable, 5000, "standard");
        long standardMemory = runtime.totalMemory() - runtime.freeMemory() - baselineMemory;
        System.out.printf("标准实现内存增长: %.2f MB\n", standardMemory / (1024.0 * 1024.0));
        
        runtime.gc();
        
        // 测试优化实现
        DefaultMemoryManager memoryManager = new DefaultMemoryManager();
        memoryManager.enableOptimization();
        OptimizedMemTable optimizedTable = new OptimizedMemTable(10000, memoryManager);
        populateTable(optimizedTable, 5000, "optimized");
        long optimizedMemory = runtime.totalMemory() - runtime.freeMemory() - baselineMemory;
        System.out.printf("优化实现内存增长: %.2f MB\n", optimizedMemory / (1024.0 * 1024.0));
        
        // 内存使用对比
        double memoryReduction = (double)(standardMemory - optimizedMemory) / standardMemory * 100;
        System.out.printf("内存使用减少: %.2f%%\n", memoryReduction);
        System.out.println();
    }
    
    /**
     * LSMTree集成测试
     */
    private static void benchmarkLSMTreeIntegration() throws IOException {
        System.out.println("4. LSMTree集成测试");
        System.out.println("------------------");
        
        // 检查依赖是否可用
        try {
            Class.forName("io.micrometer.core.instrument.MeterRegistry");
            System.out.println("Micrometer依赖可用，执行完整测试...");
            
            String testDataDir = "./benchmark-data";
            
            // 清理测试数据
            java.io.File dir = new java.io.File(testDataDir);
            if (dir.exists()) {
                deleteRecursively(dir);
            }
            dir.mkdirs();
            
            try {
                // 标准LSMTree测试
                LSMTree standardTree = new LSMTree(testDataDir + "/standard", 1000);
                long standardTime = runLSMTreeBenchmark(standardTree, "标准LSMTree");
                
                // 优化LSMTree测试
                LSMTree optimizedTree = new LSMTree(testDataDir + "/optimized", 1000);
                long optimizedTime = runLSMTreeBenchmark(optimizedTree, "优化LSMTree");
                
                double improvement = (double)(standardTime - optimizedTime) / standardTime * 100;
                System.out.printf("LSMTree性能提升: %.2f%%\n", improvement);
                System.out.printf("标准实现: %.2f ms\n", standardTime / 1_000_000.0);
                System.out.printf("优化实现: %.2f ms\n", optimizedTime / 1_000_000.0);
                
                standardTree.close();
                optimizedTree.close();
                
            } finally {
                // 清理测试数据
                deleteRecursively(dir);
            }
        } catch (ClassNotFoundException e) {
            System.out.println("警告: Micrometer依赖不可用，跳过LSMTree集成测试");
            System.out.println("请确保在完整的Maven环境中运行此测试");
        }
    }
    
    // 辅助方法
    private static long runMemTableBenchmark(MemTable table, String testName) {
        Random random = new Random(42); // 固定种子确保可重现
        
        // 预热
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            String key = "warmup_" + i;
            String value = "value_" + random.nextInt(1000);
            table.put(key, value);
        }
        
        // 正式测试
        long startTime = System.nanoTime();
        
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            String key = testName + "_key_" + i;
            String value = "value_" + random.nextInt(10000);
            table.put(key, value);
            
            // 批量查询测试
            if (i % 100 == 0) {
                table.get(key);
            }
        }
        
        long endTime = System.nanoTime();
        return endTime - startTime;
    }
    
    private static long runLSMTreeBenchmark(LSMTree tree, String testName) throws IOException {
        Random random = new Random(42);
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            String key = testName + "_key_" + i;
            String value = "value_" + random.nextInt(10000);
            tree.put(key, value);
            
            if (i % 100 == 0) {
                tree.get(key);
            }
        }
        
        long endTime = System.nanoTime();
        return endTime - startTime;
    }
    
    private static void populateTable(MemTable table, int count, String prefix) {
        Random random = new Random(42);
        for (int i = 0; i < count; i++) {
            table.put(prefix + "_key_" + i, "value_" + random.nextInt(1000));
        }
    }
    
    private static void deleteRecursively(java.io.File file) {
        if (file.isDirectory()) {
            for (java.io.File child : file.listFiles()) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}