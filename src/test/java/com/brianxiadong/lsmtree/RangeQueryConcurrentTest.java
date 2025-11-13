package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RangeQueryConcurrentTest {
    @Test
    public void testConcurrentRanges() throws Exception {
        String dir = TestConfig.getConcurrentTestDataPath("range");
        LSMTree tree = new LSMTree(dir, 128);
        int N = 5000;
        for (int i = 0; i < N; i++) {
            String k = String.format("k%05d", i);
            tree.put(k, "v" + i);
        }

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        List<Throwable> errors = new ArrayList<>();

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
        if (!errors.isEmpty()) {
            errors.get(0).printStackTrace();
            Assert.fail("errors in concurrent range");
        }
        tree.close();
    }
}

