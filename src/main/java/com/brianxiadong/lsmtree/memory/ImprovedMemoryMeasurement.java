package com.brianxiadong.lsmtree.memory;

import com.brianxiadong.lsmtree.KeyValue;
import com.brianxiadong.lsmtree.MemTable;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.GarbageCollectorMXBean;
import java.util.List;

/**
 * 改进的内存和性能测量工具
 * 使用JVM内置的Management API进行更准确的测量
 */
public class ImprovedMemoryMeasurement {
    
    private final MemoryMXBean memoryBean;
    private final List<GarbageCollectorMXBean> gcBeans;
    
    public ImprovedMemoryMeasurement() {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
    }
    
    /**
     * 准确的内存使用测量
     */
    public MemoryMeasurementResult measureMemoryUsage(Runnable operation) {
        // 强制GC确保内存状态稳定
        forceGCAndWait();
        
        // 获取基线内存使用
        long baselineHeapUsed = getHeapUsed();
        long baselineHeapCommitted = getHeapCommitted();
        long baselineNonHeapUsed = getNonHeapUsed();
        
        // 记录初始GC统计
        long initialGcCount = getTotalGcCount();
        long initialGcTime = getTotalGcTime();
        
        // 执行测量操作
        long startTime = System.nanoTime();
        operation.run();
        long endTime = System.nanoTime();
        
        // 操作后再次GC
        forceGCAndWait();
        
        // 获取操作后内存使用
        long afterHeapUsed = getHeapUsed();
        long afterHeapCommitted = getHeapCommitted();
        long afterNonHeapUsed = getNonHeapUsed();
        
        // 获取最终GC统计
        long finalGcCount = getTotalGcCount();
        long finalGcTime = getTotalGcTime();
        
        return new MemoryMeasurementResult(
            endTime - startTime,
            baselineHeapUsed, afterHeapUsed,
            baselineHeapCommitted, afterHeapCommitted,
            baselineNonHeapUsed, afterNonHeapUsed,
            finalGcCount - initialGcCount,
            finalGcTime - initialGcTime
        );
    }
    
    /**
     * 强制GC并等待稳定
     */
    private void forceGCAndWait() {
        // 多次GC确保彻底回收
        for (int i = 0; i < 3; i++) {
            System.gc();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * 获取堆内存使用量
     */
    private long getHeapUsed() {
        return memoryBean.getHeapMemoryUsage().getUsed();
    }
    
    /**
     * 获取堆内存承诺量
     */
    private long getHeapCommitted() {
        return memoryBean.getHeapMemoryUsage().getCommitted();
    }
    
    /**
     * 获取非堆内存使用量
     */
    private long getNonHeapUsed() {
        return memoryBean.getNonHeapMemoryUsage().getUsed();
    }
    
    /**
     * 获取总GC次数
     */
    private long getTotalGcCount() {
        return gcBeans.stream()
            .mapToLong(GarbageCollectorMXBean::getCollectionCount)
            .sum();
    }
    
    /**
     * 获取总GC时间
     */
    private long getTotalGcTime() {
        return gcBeans.stream()
            .mapToLong(GarbageCollectorMXBean::getCollectionTime)
            .sum();
    }
    
    /**
     * 内存测量结果类
     */
    public static class MemoryMeasurementResult {
        private final long operationTimeNanos;
        private final long baselineHeapUsed;
        private final long afterHeapUsed;
        private final long baselineHeapCommitted;
        private final long afterHeapCommitted;
        private final long baselineNonHeapUsed;
        private final long afterNonHeapUsed;
        private final long gcCountIncrease;
        private final long gcTimeIncrease;
        
        public MemoryMeasurementResult(long operationTimeNanos,
                                     long baselineHeapUsed, long afterHeapUsed,
                                     long baselineHeapCommitted, long afterHeapCommitted,
                                     long baselineNonHeapUsed, long afterNonHeapUsed,
                                     long gcCountIncrease, long gcTimeIncrease) {
            this.operationTimeNanos = operationTimeNanos;
            this.baselineHeapUsed = baselineHeapUsed;
            this.afterHeapUsed = afterHeapUsed;
            this.baselineHeapCommitted = baselineHeapCommitted;
            this.afterHeapCommitted = afterHeapCommitted;
            this.baselineNonHeapUsed = baselineNonHeapUsed;
            this.afterNonHeapUsed = afterNonHeapUsed;
            this.gcCountIncrease = gcCountIncrease;
            this.gcTimeIncrease = gcTimeIncrease;
        }
        
        // Getters
        public long getOperationTimeMillis() { return operationTimeNanos / 1_000_000; }
        public long getHeapUsedIncrease() { return afterHeapUsed - baselineHeapUsed; }
        public long getHeapCommittedIncrease() { return afterHeapCommitted - baselineHeapCommitted; }
        public long getNonHeapUsedIncrease() { return afterNonHeapUsed - baselineNonHeapUsed; }
        public long getGcCountIncrease() { return gcCountIncrease; }
        public long getGcTimeIncrease() { return gcTimeIncrease; }
        
        public double getHeapUsageRatio() {
            return (double) afterHeapUsed / afterHeapCommitted;
        }
        
        @Override
        public String toString() {
            return String.format(
                "MemoryMeasurementResult{\n" +
                "  操作时间: %d ms\n" +
                "  堆内存增长: %d bytes (%.2f MB)\n" +
                "  堆内存使用率: %.1f%%\n" +
                "  非堆内存增长: %d bytes (%.2f MB)\n" +
                "  GC次数增加: %d\n" +
                "  GC时间增加: %d ms\n" +
                "}",
                getOperationTimeMillis(),
                getHeapUsedIncrease(), getHeapUsedIncrease() / (1024.0 * 1024.0),
                getHeapUsageRatio() * 100,
                getNonHeapUsedIncrease(), getNonHeapUsedIncrease() / (1024.0 * 1024.0),
                getGcCountIncrease(),
                getGcTimeIncrease()
            );
        }
    }
    
    /**
     * 运行改进的性能测试
     */
    public static void runImprovedBenchmark() {
        System.out.println("=== 改进的内存和性能测量测试 ===\n");
        
        ImprovedMemoryMeasurement measurer = new ImprovedMemoryMeasurement();
        
        // 测试数据规模 - 去除1000万条记录测试
        int[] dataSizes = {100_000, 1_000_000};
        
        for (int dataSize : dataSizes) {
            System.out.println("测试数据规模: " + dataSize + " 条记录");
            System.out.println("-".concat(new String(new char[50]).replace('\0', '-')));
            
            // 标准实现测试
            MemoryMeasurementResult standardResult = measurer.measureMemoryUsage(() -> {
                MemTable table = new MemTable(dataSize + 1000);
                for (int i = 0; i < dataSize; i++) {
                    table.put("std_key_" + i, "value_" + i);
                }
                // 确保对象不会被过早回收
                try { Thread.sleep(10); } catch (InterruptedException e) {}
            });
            
            // 优化实现测试
            MemoryMeasurementResult optimizedResult = measurer.measureMemoryUsage(() -> {
                DefaultMemoryManager memoryManager = new DefaultMemoryManager();
                memoryManager.enableOptimization();
                OptimizedMemTable table = new OptimizedMemTable(dataSize + 1000, memoryManager);
                for (int i = 0; i < dataSize; i++) {
                    table.put("opt_key_" + i, "value_" + i);
                }
                // 获取统计信息确保对象池被使用
                OptimizedMemTable.MemoryOptimizationStats stats = table.getOptimizationStats();
                System.out.println("  对象池统计: " + stats.getKeyValuePoolStats());
                try { Thread.sleep(10); } catch (InterruptedException e) {}
                memoryManager.shutdown();
            });
            
            // 输出结果比较
            System.out.println("标准实现结果:");
            System.out.println(standardResult);
            System.out.println("\n优化实现结果:");
            System.out.println(optimizedResult);
            
            // 计算改进百分比
            double timeImprovement = (double)(standardResult.getOperationTimeMillis() - 
                                            optimizedResult.getOperationTimeMillis()) / 
                                   standardResult.getOperationTimeMillis() * 100;
            
            // 修复内存优化计算逻辑 - 使用绝对值比较
            double memoryImprovement;
            long standardHeapIncrease = standardResult.getHeapUsedIncrease();
            long optimizedHeapIncrease = optimizedResult.getHeapUsedIncrease();
            
            // 如果两者都很小(<1KB)，认为内存使用基本相同
            if (Math.abs(standardHeapIncrease) < 1024 && Math.abs(optimizedHeapIncrease) < 1024) {
                memoryImprovement = 0.0;
            } else if (standardHeapIncrease <= 0) {
                // 标准实现内存减少或不变
                if (optimizedHeapIncrease <= 0) {
                    memoryImprovement = 0.0; // 两者都减少，难以比较
                } else {
                    memoryImprovement = -100.0; // 优化实现增加内存使用
                }
            } else {
                // 标准实现内存增加，计算相对优化
                memoryImprovement = (double)(standardHeapIncrease - optimizedHeapIncrease) / 
                                  Math.abs(standardHeapIncrease) * 100;
            }
            
            System.out.printf("\n性能改进: 时间提升 %.2f%%, 内存优化 %.2f%%\n", 
                            timeImprovement, memoryImprovement);
            System.out.printf("GC压力减少: %d 次GC (标准:%d次, 优化:%d次)\n", 
                            standardResult.getGcCountIncrease() - optimizedResult.getGcCountIncrease(),
                            standardResult.getGcCountIncrease(),
                            optimizedResult.getGcCountIncrease());
        }
        
        System.out.println("=== 测试完成 ===");
    }
    
    public static void main(String[] args) {
        runImprovedBenchmark();
    }
}