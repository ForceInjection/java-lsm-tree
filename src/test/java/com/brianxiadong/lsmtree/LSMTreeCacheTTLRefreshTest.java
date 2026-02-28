package com.brianxiadong.lsmtree;

import com.brianxiadong.lsmtree.cache.CacheManagerImpl;
import com.brianxiadong.lsmtree.cache.CacheStrategy;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

/**
 * LSM Tree缓存TTL刷新测试类
 * 测试缓存TTL过期后的刷新机制
 */
public class LSMTreeCacheTTLRefreshTest {
    @Test
    public void testTTLExpiryThenRefreshFromUnderlying() throws Exception {
        TestLogger log = new TestLogger("缓存TTL刷新测试");
        log.start("测试缓存TTL过期后从底层存储刷新");
        
        String dir = System.getProperty("java.io.tmpdir") + File.separator + "cache_ttl_" + System.currentTimeMillis();
        LSMTree tree = new LSMTree(dir, 100);
        CacheManagerImpl cm = new CacheManagerImpl(1000, 100, CacheStrategy.LRU);
        cm.setRowCacheTTLMillis(5);
        tree.setCacheManager(cm);
        log.data("缓存策略", "LRU");
        log.data("TTL", "5ms");
        
        log.step("插入数据k=v");
        tree.put("k","v");
        Assert.assertEquals("v", tree.get("k"));
        log.data("插入并读取", "k=v");
        
        log.step("等待20ms让TTL过期");
        Thread.sleep(20);
        log.data("等待时间", "20ms（TTL=5ms）");
        
        log.step("再次读取，验证从底层存储刷新");
        String value = tree.get("k");
        log.data("读取结果", value);
        Assert.assertEquals("v", value);
        log.assertSuccess("TTL过期后成功从底层存储刷新数据");
        tree.close();
        log.pass();
    }
}