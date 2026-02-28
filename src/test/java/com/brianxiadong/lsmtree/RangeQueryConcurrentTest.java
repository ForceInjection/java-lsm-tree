package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 范围查询并发测试类
 * 测试并发范围查询的正确性
 */
public class RangeQueryConcurrentTest {
    @Test
    public void testConcurrentRanges() throws Exception {
        TestLogger log = new TestLogger("并发范围查询测试");
        log.start("测试多线程并发执行范围查询");
        
        String dir = TestConfig.getConcurrentTestDataPath("range");
        LSMTree tree = new LSMTree(dir, 128);
        int N = 5000;
        log.step("插入" + N + "条数据");
        for (int i = 0; i < N; i++) {
            String k = String.format("k%05d", i);
            tree.put(k, "v" + i);
        }
        log.data("数据条数", N);

        int threads = 8;
        log.step("启动" + threads + "个线程并发范围查询");
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        List<Throwable> errors = new ArrayList<>();
        log.data("线程数", threads);

        for (int t = 0; t < threads; t++) {
            final int idx = t;
            pool.submit(() -> {
                try {
                    int start = idx * (N / threads);
                    int end = Math.min(N - 1, start + (N / threads) - 1);
                    String sk = String.format("k%05d", start);
                    String ek = String.format("k%05d", end);
                    Iterator<KeyValue> it = tree.range(sk, ek, true, true);
                    int c = 0;
                    while (it.hasNext()) {
                        KeyValue kv = it.next();
                        Assert.assertTrue(kv.getKey().compareTo(sk) >= 0);
                        Assert.assertTrue(kv.getKey().compareTo(ek) <= 0);
                        c++;
                    }
                    Assert.assertTrue(c > 0);
                } catch (Throwable e) {
                    errors.add(e);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        pool.shutdownNow();
        
        log.step("检查并发执行结果");
        log.data("错误数", errors.size());
        if (!errors.isEmpty()) {
            errors.get(0).printStackTrace();
            Assert.fail("errors in concurrent range");
        }
        log.assertSuccess("并发范围查询全部正确");
        tree.close();
        log.pass();
    }
}

