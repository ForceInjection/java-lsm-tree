package com.brianxiadong.lsmtree.memory;

/**
 * 内存优化测试类
 * 用于验证T8任务的内存管理功能
 */
public class MemoryOptimizationTest {
    
    public static void main(String[] args) {
        System.out.println("=== T8 内存优化测试 ===");
        
        // 创建内存管理器
        DefaultMemoryManager memoryManager = new DefaultMemoryManager();
        
        // 测试基础功能
        testBasicFunctionality(memoryManager);
        
        // 测试对象池功能
        testObjectPooling(memoryManager);
        
        // 测试堆外内存
        testDirectMemory(memoryManager);
        
        // 测试内存监控
        testMemoryMonitoring(memoryManager);
        
        // 测试GC配置
        testGCConfiguration(memoryManager);
        
        System.out.println("=== 测试完成 ===");
    }
    
    private static void testBasicFunctionality(DefaultMemoryManager memoryManager) {
        System.out.println("\n1. 测试基础功能:");
        
        // 检查初始状态
        System.out.println("  初始优化状态: " + memoryManager.isOptimizationEnabled());
        
        // 启用优化
        memoryManager.enableOptimization();
        System.out.println("  启用后状态: " + memoryManager.isOptimizationEnabled());
        
        // 获取内存统计
        MemoryUsageStats stats = memoryManager.getMemoryStats();
        System.out.println("  内存统计: " + stats);
    }
    
    private static void testObjectPooling(DefaultMemoryManager memoryManager) {
        System.out.println("\n2. 测试对象池功能:");
        
        // 获取字符串构建器池
        ObjectPool<StringBuilder> stringBuilderPool = memoryManager.getObjectPool(StringBuilder.class);
        System.out.println("  StringBuilder池创建成功");
        
        // 测试借用和归还
        StringBuilder sb1 = stringBuilderPool.borrowObject();
        sb1.append("Hello");
        System.out.println("  借用StringBuilder: " + sb1.toString());
        
        stringBuilderPool.returnObject(sb1);
        System.out.println("  归还StringBuilder成功");
        
        // 获取统计信息
        PoolStats poolStats = stringBuilderPool.getStats();
        System.out.println("  池统计: " + poolStats);
    }
    
    private static void testDirectMemory(DefaultMemoryManager memoryManager) {
        System.out.println("\n3. 测试堆外内存:");
        
        // 分配堆外内存
        java.nio.ByteBuffer directBuffer = memoryManager.allocate(1024, true);
        System.out.println("  分配堆外内存: " + directBuffer.capacity() + " bytes");
        
        // 分配堆内内存
        java.nio.ByteBuffer heapBuffer = memoryManager.allocate(512, false);
        System.out.println("  分配堆内内存: " + heapBuffer.capacity() + " bytes");
        
        // 释放内存
        memoryManager.deallocate(directBuffer);
        memoryManager.deallocate(heapBuffer);
        System.out.println("  内存释放完成");
    }
    
    private static void testMemoryMonitoring(DefaultMemoryManager memoryManager) {
        System.out.println("\n4. 测试内存监控:");
        
        // 获取详细内存信息
        memoryManager.getMemoryStats(); // 触发一次收集
        try {
            Thread.sleep(1000); // 等待监控收集
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        MemoryUsageStats stats = memoryManager.getMemoryStats();
        System.out.println("  内存使用统计: " + stats);
    }
    
    private static void testGCConfiguration(DefaultMemoryManager memoryManager) {
        System.out.println("\n5. 测试GC配置:");
        
        // 获取当前配置
        GCConfig currentConfig = memoryManager.getCurrentGCConfig();
        System.out.println("  当前GC配置: " + currentConfig);
        
        // 更新配置
        GCConfig newConfig = new GCConfig();
        newConfig.setHeapSizeMB(8192); // 8GB
        newConfig.setMaxGCPauseMillis(100);
        memoryManager.updateGCConfig(newConfig);
        
        System.out.println("  更新后GC配置: " + memoryManager.getCurrentGCConfig());
    }
}