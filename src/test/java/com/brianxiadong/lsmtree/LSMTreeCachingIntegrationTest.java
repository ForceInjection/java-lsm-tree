package com.brianxiadong.lsmtree;

import com.brianxiadong.lsmtree.cache.CacheManagerImpl;
import com.brianxiadong.lsmtree.cache.CacheStrategy;
import com.brianxiadong.lsmtree.cache.CacheType;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

/**
 * LSM Tree缓存集成测试类
 * 测试LSM Tree与缓存的集成效果
 */
public class LSMTreeCachingIntegrationTest {
    @Test
    public void testHotKeyReadHitRateOver80Percent() throws Exception {
        TestLogger log = new TestLogger("缓存集成热点key测试");
        log.start("测试热点key的缓存命中率超过80%");
        
        String dir = System.getProperty("java.io.tmpdir") + File.separator + "cache_it_" + System.currentTimeMillis();
        LSMTree tree = new LSMTree(dir, 1000);
        CacheManagerImpl cm = new CacheManagerImpl(10000, 1000, CacheStrategy.LRU);
        tree.setCacheManager(cm);
        log.data("缓存策略", "LRU");
        log.data("缓存容量", 10000);

        log.step("插入1000条数据");
        for (int i = 0; i < 1000; i++) tree.put("k"+i, "v"+i);
        log.data("数据条数", 1000);
        
        log.step("预热缓存");
        for (int i = 0; i < 1000; i++) tree.get("k"+i);

        log.step("执行10000次热点key访问（90%访问k42）");
        int ops = 10000;
        for (int i = 0; i < ops; i++) {
            String key = (i % 10 == 0) ? ("k" + (i % 100)) : "k42"; // 90% 访问热点 k42
            tree.get(key);
        }
        log.data("总访问次数", ops);
        log.data("热点key", "k42（90%访问）");

        log.step("检查缓存命中率");
        double hitRatio = cm.getStats(CacheType.ROW).getHitRatio();
        log.data("命中率", String.format("%.2f%%", hitRatio * 100));
        Assert.assertTrue(hitRatio >= 0.8);
        log.assertSuccess("缓存命中率超过80%");
        tree.close();
        log.pass();
    }
}