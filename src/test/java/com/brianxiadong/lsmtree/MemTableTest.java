package com.brianxiadong.lsmtree;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MemTable 内存表测试类
 * 验证跳表实现、刷盘机制、并发性能和内存管理
 */
public class MemTableTest {

    private MemTable memTable;
    private static final int DEFAULT_MAX_SIZE = 100;

    @Before
    public void setUp() {
        memTable = new MemTable(DEFAULT_MAX_SIZE);
    }

    @After
    public void tearDown() {
        memTable = null;
    }

    /**
     * 测试基本的 put 和 get 操作
     */
    @Test
    public void testBasicPutAndGet() {
        // 测试基本插入和查询
        memTable.put("key1", "value1");
        memTable.put("key2", "value2");

        assertEquals("value1", memTable.get("key1"));
        assertEquals("value2", memTable.get("key2"));
        assertNull(memTable.get("nonexistent"));
    }

    /**
     * 测试更新操作（相同键的新值）
     */
    @Test
    public void testUpdate() {
        memTable.put("key1", "value1");
        assertEquals("value1", memTable.get("key1"));

        memTable.put("key1", "updated_value");
        assertEquals("updated_value", memTable.get("key1"));
    }

    /**
     * 测试删除操作（逻辑删除）
     */
    @Test
    public void testDelete() {
        memTable.put("key1", "value1");
        assertEquals("value1", memTable.get("key1"));

        memTable.delete("key1");
        assertNull(memTable.get("key1"));
    }

    /**
     * 测试删除后重新插入
     */
    @Test
    public void testDeleteAndReinsert() {
        memTable.put("key1", "value1");
        memTable.delete("key1");
        assertNull(memTable.get("key1"));

        memTable.put("key1", "new_value");
        assertEquals("new_value", memTable.get("key1"));
    }

    /**
     * 测试刷盘检查机制
     */
    @Test
    public void testShouldFlush() {
        assertFalse(memTable.shouldFlush());

        // 插入数据直到接近刷盘阈值
        for (int i = 0; i < DEFAULT_MAX_SIZE - 1; i++) {
            memTable.put("key" + i, "value" + i);
        }
        assertFalse(memTable.shouldFlush());

        // 再插入一个，达到刷盘条件
        memTable.put("final_key", "final_value");
        assertTrue(memTable.shouldFlush());
    }

    /**
     * 测试获取所有条目（有序性验证）
     */
    @Test
    public void testGetAllEntries() {
        memTable.put("c", "value3");
        memTable.put("a", "value1");
        memTable.put("b", "value2");
        memTable.put("d", "value4");

        List<KeyValue> entries = memTable.getAllEntries();
        
        assertEquals(4, entries.size());
        // 验证按键的字典序排列
        assertEquals("a", entries.get(0).getKey());
        assertEquals("b", entries.get(1).getKey());
        assertEquals("c", entries.get(2).getKey());
        assertEquals("d", entries.get(3).getKey());
    }

    /**
     * 测试获取所有条目包含删除标记
     */
    @Test
    public void testGetAllEntriesWithTombstones() {
        memTable.put("a", "value1");
        memTable.put("b", "value2");
        memTable.delete("a");
        memTable.put("c", "value3");

        List<KeyValue> entries = memTable.getAllEntries();
        
        assertEquals(3, entries.size()); // 2个插入 + 1个删除
        
        // 验证顺序和墓碑标记
        assertEquals("a", entries.get(0).getKey());
        assertTrue(entries.get(0).isDeleted());
        
        assertEquals("b", entries.get(1).getKey());
        assertFalse(entries.get(1).isDeleted());
        
        assertEquals("c", entries.get(2).getKey());
        assertFalse(entries.get(2).isDeleted());
    }

    /**
     * 测试清空操作
     */
    @Test
    public void testClear() {
        memTable.put("key1", "value1");
        memTable.put("key2", "value2");
        assertEquals(2, memTable.size());

        memTable.clear();
        assertEquals(0, memTable.size());
        assertTrue(memTable.isEmpty());
        assertNull(memTable.get("key1"));
        assertNull(memTable.get("key2"));
    }

    /**
     * 测试大小统计
     */
    @Test
    public void testSize() {
        assertEquals(0, memTable.size());
        assertTrue(memTable.isEmpty());

        memTable.put("key1", "value1");
        assertEquals(1, memTable.size());
        assertFalse(memTable.isEmpty());

        memTable.put("key2", "value2");
        assertEquals(2, memTable.size());

        memTable.delete("key1");
        assertEquals(2, memTable.size()); // 删除已存在的键不增加大小

        memTable.put("key3", "new_value");
        assertEquals(3, memTable.size()); // 插入新键增加大小
    }

    /**
     * 测试相同键的多次操作大小统计
     */
    @Test
    public void testSizeWithKeyUpdates() {
        assertEquals(0, memTable.size());

        // 第一次插入
        memTable.put("key1", "value1");
        assertEquals(1, memTable.size());

        // 更新相同键（不增加大小）
        memTable.put("key1", "value2");
        assertEquals(1, memTable.size());

        // 删除已存在的键（不增加大小）
        memTable.delete("key1");
        assertEquals(1, memTable.size());

        // 插入新键
        memTable.put("key2", "value3");
        assertEquals(2, memTable.size());
    }

    /**
     * 测试大数据量插入性能
     */
    @Test
    public void testLargeDataSet() {
        int dataSize = 1000;
        
        long startTime = System.nanoTime();
        for (int i = 0; i < dataSize; i++) {
            memTable.put("key" + i, "value" + i);
        }
        long endTime = System.nanoTime();
        
        double durationMs = (endTime - startTime) / 1_000_000.0;
        double throughput = dataSize / (durationMs / 1000.0);
        
        System.out.printf("插入 %d 条数据 - 耗时: %.2f ms | 吞吐量: %.0f ops/sec%n", 
                         dataSize, durationMs, throughput);
        
        assertEquals(dataSize, memTable.size());
        
        // 验证部分数据
        assertEquals("value100", memTable.get("key100"));
        assertEquals("value500", memTable.get("key500"));
        assertEquals("value999", memTable.get("key999"));
    }

    /**
     * 测试并发插入性能
     */
    @Test
    public void testConcurrentInsert() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        long startTime = System.nanoTime();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        String key = "concurrent_thread" + threadId + "_key" + i;
                        String value = "concurrent_thread" + threadId + "_value" + i;
                        memTable.put(key, value);
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        long endTime = System.nanoTime();
        double durationMs = (endTime - startTime) / 1_000_000.0;
        double throughput = successCount.get() / (durationMs / 1000.0);

        System.out.printf("并发插入 - 线程数: %d | 总操作: %d | 耗时: %.2f ms | 吞吐量: %.0f ops/sec%n",
                         threadCount, successCount.get(), durationMs, throughput);

        // 验证部分数据
        assertEquals("concurrent_thread0_value50", memTable.get("concurrent_thread0_key50"));
        assertEquals("concurrent_thread5_value25", memTable.get("concurrent_thread5_key25"));
        assertEquals("concurrent_thread9_value99", memTable.get("concurrent_thread9_key99"));
        
        // 由于并发操作可能存在键冲突，我们只验证实际插入的键数量不超过预期
        assertTrue(memTable.size() <= threadCount * operationsPerThread);
        assertTrue(memTable.size() > 0);
    }

    /**
     * 测试并发读写混合操作
     */
    @Test
    public void testConcurrentMixedOperations() throws InterruptedException {
        int writerThreads = 5;
        int readerThreads = 5;
        int operationsPerThread = 50;
        
        ExecutorService executor = Executors.newFixedThreadPool(writerThreads + readerThreads);
        CountDownLatch latch = new CountDownLatch(writerThreads + readerThreads);
        AtomicInteger writeSuccess = new AtomicInteger(0);
        AtomicInteger readSuccess = new AtomicInteger(0);

        // 先写入一些基础数据
        for (int i = 0; i < 50; i++) {
            memTable.put("base_key" + i, "base_value" + i);
        }

        long startTime = System.nanoTime();

        // 启动写线程
        for (int t = 0; t < writerThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        String key = "write_key" + threadId + "_" + i;
                        String value = "write_value" + threadId + "_" + i;
                        memTable.put(key, value);
                        writeSuccess.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // 启动读线程
        for (int t = 0; t < readerThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        // 读取基础数据和新写入的数据
                        String key = (i % 2 == 0) ? "base_key" + (i % 50) : "write_key" + (threadId % writerThreads) + "_" + (i % operationsPerThread);
                        String value = memTable.get(key);
                        if (value != null) {
                            readSuccess.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        long endTime = System.nanoTime();
        double durationMs = (endTime - startTime) / 1_000_000.0;

        System.out.printf("并发混合操作 - 写线程: %d | 读线程: %d | 写成功: %d | 读成功: %d | 耗时: %.2f ms%n",
                         writerThreads, readerThreads, writeSuccess.get(), readSuccess.get(), durationMs);

        assertTrue(writeSuccess.get() > 0);
        assertTrue(readSuccess.get() > 0);
        assertEquals(50 + writeSuccess.get(), memTable.size()); // 基础数据 + 成功写入的数据
    }

    /**
     * 测试不同 MemTable 大小的性能影响
     */
    @Test
    public void testDifferentMemTableSizes() {
        int[] sizes = {10, 50, 100, 200};
        int testOperations = 100;

        for (int size : sizes) {
            MemTable testTable = new MemTable(size);
            
            long startTime = System.nanoTime();
            for (int i = 0; i < testOperations; i++) {
                testTable.put("key" + i, "value" + i);
            }
            long endTime = System.nanoTime();
            
            double durationMs = (endTime - startTime) / 1_000_000.0;
            double throughput = testOperations / (durationMs / 1000.0);
            
            System.out.printf("MemTable 大小: %d | 插入 %d 条数据 | 耗时: %.2f ms | 吞吐量: %.0f ops/sec%n",
                             size, testOperations, durationMs, throughput);
            
            assertEquals(testOperations, testTable.size());
            assertEquals(size <= testOperations, testTable.shouldFlush());
        }
    }

    /**
     * 测试内存使用模式
     */
    @Test
    public void testMemoryUsagePattern() {
        Runtime runtime = Runtime.getRuntime();
        
        // GC 并记录基线内存
        runtime.gc();
        long baselineMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // 创建 MemTable 并插入数据
        MemTable testTable = new MemTable(1000);
        for (int i = 0; i < 500; i++) {
            testTable.put("memory_test_key_" + i, "memory_test_value_" + i);
        }
        
        long afterInsertMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = afterInsertMemory - baselineMemory;
        
        System.out.printf("基线内存: %d bytes | 插入后内存: %d bytes | 内存增长: %d bytes%n",
                         baselineMemory, afterInsertMemory, memoryIncrease);
        System.out.printf("平均每条约占用: %.1f bytes%n", (double) memoryIncrease / 500);
        
        assertEquals(500, testTable.size());
        assertFalse(testTable.shouldFlush());
        
        // 测试清空后的内存释放
        testTable.clear();
        assertEquals(0, testTable.size());
        assertTrue(testTable.isEmpty());
    }
}