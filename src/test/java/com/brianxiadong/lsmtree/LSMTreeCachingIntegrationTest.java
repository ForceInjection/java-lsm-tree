package com.brianxiadong.lsmtree;

import com.brianxiadong.lsmtree.cache.CacheManagerImpl;
import com.brianxiadong.lsmtree.cache.CacheStrategy;
import com.brianxiadong.lsmtree.cache.CacheType;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class LSMTreeCachingIntegrationTest {
    @Test
    public void testHotKeyReadHitRateOver80Percent() throws Exception {
        String dir = System.getProperty("java.io.tmpdir") + File.separator + "cache_it_" + System.currentTimeMillis();
        LSMTree tree = new LSMTree(dir, 1000);
        CacheManagerImpl cm = new CacheManagerImpl(10000, 1000, CacheStrategy.LRU);
        tree.setCacheManager(cm);

        for (int i = 0; i < 1000; i++) tree.put("k"+i, "v"+i);
        for (int i = 0; i < 1000; i++) tree.get("k"+i);

        int ops = 10000;
        for (int i = 0; i < ops; i++) {
            String key = (i % 10 == 0) ? ("k" + (i % 100)) : "k42"; // 90% 访问热点 k42
            tree.get(key);
        }

        double hitRatio = cm.getStats(CacheType.ROW).getHitRatio();
        Assert.assertTrue(hitRatio >= 0.8);
        tree.close();
    }
}