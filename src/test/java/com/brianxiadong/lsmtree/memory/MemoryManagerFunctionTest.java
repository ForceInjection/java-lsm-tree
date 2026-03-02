package com.brianxiadong.lsmtree.memory;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内存管理模块功能测试
 * 验证 MemoryManager, ObjectPool 和 DirectMemoryManager 的功能
 */
public class MemoryManagerFunctionTest {

    @Test
    public void testDirectMemoryAllocation() {
        DirectMemoryManager dmm = new DirectMemoryManager();
        
        // 分配 1KB
        ByteBuffer buf1 = dmm.allocate(1024);
        Assert.assertTrue(buf1.isDirect());
        Assert.assertEquals(1024, buf1.capacity());
        
        // 分配 2KB
        ByteBuffer buf2 = dmm.allocate(2048);
        Assert.assertTrue(buf2.isDirect());
        Assert.assertEquals(2048, buf2.capacity());
        
        // 验证统计
        DirectMemoryManager.DirectMemoryStats stats = dmm.getStats();
        Assert.assertEquals(1024 + 2048, stats.getTotalAllocated());
        
        // 释放 buf1
        dmm.deallocate(buf1);
        
        // 验证 buf1 进入池中
        stats = dmm.getStats();
        Assert.assertEquals(1, stats.getPoolSize());
        
        // 再次分配 1KB，应该重用 buf1
        ByteBuffer buf3 = dmm.allocate(1024);
        stats = dmm.getStats();
        Assert.assertEquals(0, stats.getPoolSize()); // 池空了
        Assert.assertEquals(buf1, buf3); // 重用了池中的 buf1
    }

    @Test
    public void testObjectPool() throws InterruptedException {
        // 创建一个 Integer 包装类的池（仅作示例，通常池化昂贵对象）
        // 使用自定义工厂
        AtomicInteger counter = new AtomicInteger(0);
        ObjectPool<Integer> pool = new GenericObjectPool<>(Integer.class, (clazz) -> counter.incrementAndGet());
        
        // 借用对象
        Integer i1 = pool.borrowObject();
        Assert.assertEquals(Integer.valueOf(1), i1);
        
        Integer i2 = pool.borrowObject();
        Assert.assertEquals(Integer.valueOf(2), i2);
        
        // 归还对象
        pool.returnObject(i1);
        
        // 再次借用，应该拿到归还的 i1
        Integer i3 = pool.borrowObject();
        Assert.assertEquals(Integer.valueOf(1), i3);
        
        PoolStats stats = pool.getStats();
        Assert.assertEquals(2, stats.getActiveCount());
    }
    
    @Test
    public void testMemoryManagerOptimizationSwitch() {
        MemoryManager mm = new DefaultMemoryManager();
        
        // 默认未启用优化
        Assert.assertFalse(mm.isOptimizationEnabled());
        
        ByteBuffer heapBuf = mm.allocate(1024, false);
        Assert.assertFalse(heapBuf.isDirect());
        
        ByteBuffer directBuf = mm.allocate(1024, true);
        Assert.assertTrue(directBuf.isDirect());
        
        // 启用优化
        mm.enableOptimization();
        Assert.assertTrue(mm.isOptimizationEnabled());
        
        // 再次分配 Direct，应该走池化逻辑（可以通过 Mock 或反射验证，这里仅验证功能可用性）
        ByteBuffer optimizedBuf = mm.allocate(1024, true);
        Assert.assertTrue(optimizedBuf.isDirect());
        
        mm.deallocate(optimizedBuf);
        // 应该回收到池中
        
        mm.disableOptimization();
        Assert.assertFalse(mm.isOptimizationEnabled());
    }
    
    @Test
    public void testObjectPoolConcurrency() throws InterruptedException {
        GenericObjectPool<StringBuilder> pool = new GenericObjectPool<>(StringBuilder.class);
        int threads = 10;
        int iterations = 100;
        ExecutorService es = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger(0);
        
        for (int i = 0; i < threads; i++) {
            es.submit(() -> {
                try {
                    for (int j = 0; j < iterations; j++) {
                        StringBuilder sb = pool.borrowObject();
                        sb.append("test");
                        sb.setLength(0); // reset
                        pool.returnObject(sb);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        es.shutdown();
        
        Assert.assertEquals(0, errors.get());
        PoolStats stats = pool.getStats();
        Assert.assertEquals(0, stats.getActiveCount());
        Assert.assertTrue(stats.getIdleCount() > 0);
    }
}
