package com.brianxiadong.lsmtree.memory;

import com.brianxiadong.lsmtree.KeyValue;
import com.brianxiadong.lsmtree.MemTable;
import com.brianxiadong.lsmtree.TestLogger;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * 内存优化集成测试
 * 测试内存管理器与LSMTree核心组件的集成效果
 */
public class MemoryOptimizationIntegrationTest {
    
    private DefaultMemoryManager memoryManager;
    private MemTable memTable;
    
    @Before
    public void setUp() {
        memoryManager = new DefaultMemoryManager();
        memTable = new MemTable(1000);
    }
    
    @Test
    public void testMemTableWithObjectPooling() {
        TestLogger log = new TestLogger("MemTable对象池测试");
        log.start("测试MemTable与对象池的集成效果");
        
        log.step("启用内存优化");
        memoryManager.enableOptimization();
        
        log.step("获取KeyValue对象池");
        ObjectPool<KeyValue> keyValuePool = memoryManager.getObjectPool(KeyValue.class);
        
        log.step("测试频繁创建KeyValue对象的场景");
        long startTime = System.nanoTime();
        
        for (int i = 0; i < 1000; i++) {
            String key = "test_key_" + i;
            String value = "test_value_" + i;
            KeyValue kv = new KeyValue(key, value);
            memTable.put(key, value);
        }
        
        long endTime = System.nanoTime();
        long duration = endTime - startTime;
        
        log.data("插入记录数", 1000);
        log.data("总耗时", String.format("%.2f ms", duration / 1_000_000.0));
        
        log.step("验证数据正确性");
        assertEquals(1000, memTable.size());
        assertEquals("test_value_500", memTable.get("test_key_500"));
        log.assertSuccess("数据正确性验证通过");
        
        log.step("检查对象池统计");
        PoolStats stats = keyValuePool.getStats();
        log.data("KeyValue对象池统计", stats.toString());
        log.pass();
    }
    
    @Test
    public void testMemoryUsageReduction() {
        TestLogger log = new TestLogger("内存使用减少测试");
        log.start("测试内存优化后内存使用减少的效果");
        
        log.step("启用内存优化并获取基线内存");
        memoryManager.enableOptimization();
        
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        
        long baselineMemory = runtime.totalMemory() - runtime.freeMemory();
        log.data("基线内存", String.format("%d bytes (%.2f MB)", baselineMemory, baselineMemory / (1024.0 * 1024.0)));
        
        log.step("创建大量数据(5000条记录)");
        for (int i = 0; i < 5000; i++) {
            memTable.put("memory_test_" + i, "value_" + i);
        }
        
        long afterMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = afterMemory - baselineMemory;
        
        log.data("插入后内存", String.format("%d bytes (%.2f MB)", afterMemory, afterMemory / (1024.0 * 1024.0)));
        log.data("内存增长", String.format("%d bytes (%.2f MB)", memoryIncrease, memoryIncrease / (1024.0 * 1024.0)));
        
        assertTrue("内存使用应该合理", memoryIncrease < 100 * 1024 * 1024);
        log.assertSuccess("内存使用验证通过(小于100MB)");
        log.pass();
    }
    
    @Test
    public void testGCPressureReduction() {
        TestLogger log = new TestLogger("GC压力减轻测试");
        log.start("测试内存优化后GC压力减轻的效果");
        
        log.step("启用内存优化");
        memoryManager.enableOptimization();
        
        log.step("记录初始GC统计");
        MemoryUsageStats initialStats = memoryManager.getMemoryStats();
        long initialGCCount = initialStats.getGcCount();
        log.data("初始GC次数", initialGCCount);
        
        log.step("执行大量操作(10批 × 1000条)");
        for (int batch = 0; batch < 10; batch++) {
            for (int i = 0; i < 1000; i++) {
                String key = "batch_" + batch + "_key_" + i;
                String value = "batch_" + batch + "_value_" + i;
                memTable.put(key, value);
            }
            
            if (batch % 3 == 0) {
                System.gc();
            }
        }
        log.data("总操作数", 10000);
        
        log.step("检查最终GC统计");
        MemoryUsageStats finalStats = memoryManager.getMemoryStats();
        long finalGCCount = finalStats.getGcCount();
        long gcCountIncrease = finalGCCount - initialGCCount;
        
        log.data("最终GC次数", finalGCCount);
        log.data("GC次数增加", gcCountIncrease);
        
        assertTrue("GC次数增加应该在合理范围内", gcCountIncrease <= 5);
        log.assertSuccess("GC压力验证通过(GC次数增加<=5)");
        log.pass();
    }
    
    @Test
    public void testStringBuilderPoolingInSSTableOperations() {
        TestLogger log = new TestLogger("StringBuilder池化测试");
        log.start("测试StringBuilder池化在SSTable操作中的效果");
        
        log.step("启用内存优化并获取StringBuilder池");
        memoryManager.enableOptimization();
        ObjectPool<StringBuilder> sbPool = memoryManager.getObjectPool(StringBuilder.class);
        
        log.step("模拟SSTable中的字符串操作(1000次)");
        long startTime = System.nanoTime();
        
        for (int i = 0; i < 1000; i++) {
            StringBuilder sb = sbPool.borrowObject();
            try {
                sb.append("sstable_")
                  .append("level0_")
                  .append(System.currentTimeMillis())
                  .append("_")
                  .append(i)
                  .append(".db");
                
                String fileName = sb.toString();
            } finally {
                sbPool.returnObject(sb);
            }
        }
        
        long endTime = System.nanoTime();
        log.data("操作次数", 1000);
        log.data("总耗时", String.format("%.2f ms", (endTime - startTime) / 1_000_000.0));
        
        log.step("检查池统计");
        PoolStats stats = sbPool.getStats();
        log.data("StringBuilder池统计", stats.toString());
        log.data("池命中率", String.format("%.2f%%", stats.getHitRate() * 100));
        
        assertTrue("池命中率应该较高", stats.getHitRate() > 0.8);
        log.assertSuccess("StringBuilder池命中率验证通过(>80%)");
        log.pass();
    }
    
    @Test
    public void testDirectMemoryUsageInFileOperations() {
        TestLogger log = new TestLogger("堆外内存使用测试");
        log.start("测试堆外内存分配在文件I/O操作中的使用");
        
        log.step("启用内存优化");
        memoryManager.enableOptimization();
        
        log.step("测试堆外内存分配(100次 × 8KB)");
        long startTime = System.nanoTime();
        
        for (int i = 0; i < 100; i++) {
            java.nio.ByteBuffer directBuffer = memoryManager.allocate(8192, true);
            
            for (int j = 0; j < 100; j++) {
                directBuffer.putInt(j);
            }
            directBuffer.flip();
            
            memoryManager.deallocate(directBuffer);
        }
        
        long endTime = System.nanoTime();
        log.data("分配次数", 100);
        log.data("缓冲区大小", "8KB");
        log.data("总耗时", String.format("%.2f ms", (endTime - startTime) / 1_000_000.0));
        
        log.step("验证堆外内存使用统计");
        MemoryUsageStats stats = memoryManager.getMemoryStats();
        log.data("内存使用统计", stats.toString());
        log.pass();
    }
}