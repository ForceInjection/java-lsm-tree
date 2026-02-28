package com.brianxiadong.lsmtree.memory;

import com.brianxiadong.lsmtree.KeyValue;
import com.brianxiadong.lsmtree.MemTable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 大规模数据内存优化性能测试
 * 验证在大数据量场景下的优化效果
 */
public class LargeScaleMemoryBenchmark {
    
    private static final int SMALL_DATA_SIZE = 10_000;      // 1万条数据
    private static final int MEDIUM_DATA_SIZE = 100_000;    // 10万条数据  
    private static final int LARGE_DATA_SIZE = 1_000_000;   // 100万条数据
    
    public static void main(String[] args) {
        System.out.println("=== 大规模数据内存优化性能测试 ===\n");
        
        runScaleTest("小规模测试 (1万条)", SMALL_DATA_SIZE);
        runScaleTest("中规模测试 (10万条)", MEDIUM_DATA_SIZE);
        runScaleTest("大规模测试 (100万条)", LARGE_DATA_SIZE);
        
        System.out.println("=== 测试完成 ===");
    }
    
    private static void runScaleTest(String testName, int dataSize) {
        System.out.println(testName);
        System.out.println(createSeparator(testName.length()));
        
        // 测试标准实现
        long standardTime = testStandardImplementation(dataSize);
        
        // 测试优化实现
        long optimizedTime = testOptimizedImplementation(dataSize);
        
        // 测试内存使用
        long standardMemory = measureMemoryUsage(() -> testStandardImplementation(dataSize));
        long optimizedMemory = measureMemoryUsage(() -> testOptimizedImplementation(dataSize));
        
        // 输出结果
        double timeImprovement = (double)(standardTime - optimizedTime) / standardTime * 100;
        
        // 修复内存优化计算逻辑
        double memoryImprovement;
        if (standardMemory == 0 && optimizedMemory == 0) {
            memoryImprovement = 0; // 两者都为0，无优化
        } else if (standardMemory == 0) {
            memoryImprovement = -100; // 标准实现无内存增长，优化实现有增长
        } else {
            memoryImprovement = (double)(standardMemory - optimizedMemory) / standardMemory * 100;
        }
        
        System.out.printf("标准实现时间: %.2f ms\n", standardTime / 1_000_000.0);
        System.out.printf("优化实现时间: %.2f ms\n", optimizedTime / 1_000_000.0);
        System.out.printf("时间性能提升: %.2f%%\n", timeImprovement);
        
        System.out.printf("标准实现内存: %.2f MB\n", standardMemory / (1024.0 * 1024.0));
        System.out.printf("优化实现内存: %.2f MB\n", optimizedMemory / (1024.0 * 1024.0));
        System.out.printf("内存使用优化: %.2f%%\n", memoryImprovement);
        
        System.out.println();
    }
    
    private static long testStandardImplementation(int dataSize) {
        MemTable table = new MemTable(dataSize + 1000);
        Random random = new Random(42);
        
        long startTime = System.nanoTime();
        
        // 插入数据
        for (int i = 0; i < dataSize; i++) {
            String key = String.format("std_key_%07d", i);
            String value = "value_" + random.nextInt(100000);
            table.put(key, value);
        }
        
        // 查询数据
        for (int i = 0; i < dataSize / 10; i++) {
            String key = String.format("std_key_%07d", random.nextInt(dataSize));
            table.get(key);
        }
        
        long endTime = System.nanoTime();
        return endTime - startTime;
    }
    
    private static long testOptimizedImplementation(int dataSize) {
        DefaultMemoryManager memoryManager = new DefaultMemoryManager();
        memoryManager.enableOptimization();
        
        OptimizedMemTable table = new OptimizedMemTable(dataSize + 1000, memoryManager);
        Random random = new Random(42);
        
        long startTime = System.nanoTime();
        
        // 插入数据
        for (int i = 0; i < dataSize; i++) {
            String key = String.format("opt_key_%07d", i);
            String value = "value_" + random.nextInt(100000);
            table.put(key, value);
        }
        
        // 查询数据
        for (int i = 0; i < dataSize / 10; i++) {
            String key = String.format("opt_key_%07d", random.nextInt(dataSize));
            table.get(key);
        }
        
        // 获取优化统计
        OptimizedMemTable.MemoryOptimizationStats stats = table.getOptimizationStats();
        System.out.printf("  对象池统计: KeyValue池=%s, StringBuilder池=%s\n", 
                         stats.getKeyValuePoolStats(), stats.getStringBuilderPoolStats());
        
        long endTime = System.nanoTime();
        return endTime - startTime;
    }
    
    private static long measureMemoryUsage(Runnable testRunnable) {
        Runtime runtime = Runtime.getRuntime();
        
        // 多次GC确保内存稳定
        for (int i = 0; i < 3; i++) {
            System.gc();
            try { Thread.sleep(50); } catch (InterruptedException e) {}
        }
        
        long baselineMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // 执行测试
        testRunnable.run();
        
        // 测试后多次GC测量内存
        for (int i = 0; i < 3; i++) {
            System.gc();
            try { Thread.sleep(50); } catch (InterruptedException e) {}
        }
        
        long afterMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryDiff = afterMemory - baselineMemory;
        
        // 确保返回非负值
        return Math.max(0, memoryDiff);
    }
    
    /**
     * GC压力测试
     */
    public static void runGCPressureTest() {
        System.out.println("=== GC压力测试 ===");
        
        DefaultMemoryManager memoryManager = new DefaultMemoryManager();
        MemoryUsageStats initialStats = memoryManager.getMemoryStats();
        
        System.out.printf("初始GC统计: 次数=%d, 时间=%dms\n", 
                         initialStats.getGcCount(), initialStats.getGcTime());
        
        // 执行大量短生命周期对象创建
        for (int batch = 0; batch < 50; batch++) {
            MemTable table = new MemTable(10000);
            
            for (int i = 0; i < 5000; i++) {
                table.put("gc_test_" + batch + "_" + i, "value_" + i);
            }
            
            // 定期强制GC
            if (batch % 10 == 0) {
                System.gc();
                try { Thread.sleep(50); } catch (InterruptedException e) {}
            }
        }
        
        MemoryUsageStats finalStats = memoryManager.getMemoryStats();
        long gcCountIncrease = finalStats.getGcCount() - initialStats.getGcCount();
        long gcTimeIncrease = finalStats.getGcTime() - initialStats.getGcTime();
        
        System.out.printf("最终GC统计: 次数=%d, 时间=%dms\n", 
                         finalStats.getGcCount(), finalStats.getGcTime());
        System.out.printf("GC次数增加: %d\n", gcCountIncrease);
        System.out.printf("GC时间增加: %d ms\n", gcTimeIncrease);
        System.out.println();
    }
    
    /**
     * 对象池效率测试
     */
    public static void runObjectPoolEfficiencyTest() {
        System.out.println("=== 对象池效率测试 ===");
        
        DefaultMemoryManager memoryManager = new DefaultMemoryManager();
        memoryManager.enableOptimization();
        
        ObjectPool<StringBuilder> sbPool = memoryManager.getObjectPool(StringBuilder.class);
        ObjectPool<KeyValue> kvPool = memoryManager.getObjectPool(KeyValue.class);
        
        // 预热
        for (int i = 0; i < 1000; i++) {
            StringBuilder sb = sbPool.borrowObject();
            sb.append("test").append(i);
            sbPool.returnObject(sb);
        }
        
        // 测试池化效率
        long startTime = System.nanoTime();
        int iterations = 10000;
        
        for (int i = 0; i < iterations; i++) {
            StringBuilder sb = sbPool.borrowObject();
            sb.append("performance_test_").append(i);
            String result = sb.toString();
            sbPool.returnObject(sb);
        }
        
        long endTime = System.nanoTime();
        double avgTime = (endTime - startTime) / (double) iterations;
        
        System.out.printf("对象池平均操作时间: %.2f ns\n", avgTime);
        System.out.printf("池统计信息: %s\n", sbPool.getStats());
        System.out.println();
    }
    
    private static String createSeparator(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append('=');
        }
        return sb.toString();
    }
}