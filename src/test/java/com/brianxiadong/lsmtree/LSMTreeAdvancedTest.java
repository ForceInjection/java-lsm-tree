package com.brianxiadong.lsmtree;

import org.junit.Test;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/**
 * LSM Tree 高级功能测试类
 * 测试并发、快速刷盘、压缩和恢复等复杂场景
 */
public class LSMTreeAdvancedTest extends LSMTreeTestBase {

    @Test
    public void testRapidFlushes() throws IOException {
        logger = new TestLogger("快速刷盘测试");
        logger.start("测试快速连续刷盘是否会导致文件名冲突");

        // 减小 MemTable 大小以触发频繁刷盘
        if (lsmTree != null) {
            lsmTree.close();
        }
        // 清理目录内容
        for (File file : testDataDir.listFiles()) {
            file.delete();
        }
        
        String path = testDataDir.getAbsolutePath();
        lsmTree = new LSMTree(path, 10); // 极小的 MemTable

        int totalItems = 100;
        logger.step("快速插入 " + totalItems + " 条数据");
        
        for (int i = 0; i < totalItems; i++) {
            lsmTree.put("key" + i, "value" + i);
            // 每次 put 都可能触发 flush (因为 size limit 是 10，虽然 entry size 可能不止 1)
            // 实际上 MemTable 检查的是 kv count 还是 size? 
            // MemTable check: currentSize >= maxSize. currentSize counts entries.
            // So every 10 entries it flushes.
        }
        
        // 强制最后一次刷盘
        lsmTree.flush();

        LSMTree.LSMTreeStats stats = lsmTree.getStats();
        logger.data("SSTable数量", stats.getSsTableCount());
        
        // 应该有至少 totalItems / 10 个 SSTables
        assertTrue("SSTable数量应该至少为 " + (totalItems / 10), stats.getSsTableCount() >= (totalItems / 10));

        logger.step("验证所有数据");
        for (int i = 0; i < totalItems; i++) {
            assertEquals("value" + i, lsmTree.get("key" + i));
        }
        logger.assertSuccess("所有数据验证通过，无文件名冲突导致的数据丢失");
        logger.pass();
    }

    @Test
    public void testConcurrentWrites() throws IOException, InterruptedException {
        logger = new TestLogger("并发写入测试");
        logger.start("多线程并发写入验证线程安全性");

        int threads = 10;
        int itemsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger(0);

        logger.step("启动 " + threads + " 个线程，每个写入 " + itemsPerThread + " 条数据");

        for (int i = 0; i < threads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < itemsPerThread; j++) {
                        String key = String.format("t%d_k%d", threadId, j);
                        String value = String.format("val_%d_%d", threadId, j);
                        lsmTree.put(key, value);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals("并发写入不应出现异常", 0, errors.get());

        logger.step("验证数据完整性");
        for (int i = 0; i < threads; i++) {
            for (int j = 0; j < itemsPerThread; j++) {
                String key = String.format("t%d_k%d", i, j);
                String expected = String.format("val_%d_%d", i, j);
                assertEquals(expected, lsmTree.get(key));
            }
        }
        logger.assertSuccess("所有并发写入的数据均可正确读取");
        logger.pass();
    }

    @Test
    public void testRecoveryWithManySSTables() throws IOException {
        logger = new TestLogger("多SSTable恢复测试");
        logger.start("测试包含大量SSTable的恢复场景");

        // 创建很多小的 SSTable
        if (lsmTree != null) {
            lsmTree.close();
        }
        // 清理目录内容
        for (File file : testDataDir.listFiles()) {
            file.delete();
        }
        
        String path = testDataDir.getAbsolutePath();
        lsmTree = new LSMTree(path, 5); 

        int batches = 20;
        int itemsPerBatch = 5; // exactly one flush per batch
        
        logger.step("生成 " + batches + " 个 SSTable");
        for (int i = 0; i < batches; i++) {
            for (int j = 0; j < itemsPerBatch; j++) {
                lsmTree.put("batch" + i + "_key" + j, "val");
            }
            lsmTree.flush(); // Force flush
        }
        
        // 验证文件是否存在
        File dir = testDataDir;
        File[] files = dir.listFiles((d, n) -> n.endsWith(".db"));
        logger.data("磁盘上的 .db 文件数", files != null ? files.length : 0);
        assertEquals(batches, files.length);

        logger.step("重启 LSM Tree");
        lsmTree.close();
        lsmTree = new LSMTree(path, 100);

        logger.step("验证数据恢复");
        LSMTree.LSMTreeStats stats = lsmTree.getStats();
        logger.data("恢复后的 SSTable 数量", stats.getSsTableCount());
        assertEquals(batches, stats.getSsTableCount());

        for (int i = 0; i < batches; i++) {
            for (int j = 0; j < itemsPerBatch; j++) {
                String key = "batch" + i + "_key" + j;
                assertEquals("val", lsmTree.get(key));
            }
        }
        logger.assertSuccess("所有数据从多个SSTable正确恢复");
        logger.pass();
    }
}
