package com.brianxiadong.lsmtree;

import com.brianxiadong.lsmtree.cache.CacheManagerImpl;
import com.brianxiadong.lsmtree.cache.CacheStrategy;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class LSMTreeCacheTTLRefreshTest {
    @Test
    public void testTTLExpiryThenRefreshFromUnderlying() throws Exception {
        String dir = System.getProperty("java.io.tmpdir") + File.separator + "cache_ttl_" + System.currentTimeMillis();
        LSMTree tree = new LSMTree(dir, 100);
        CacheManagerImpl cm = new CacheManagerImpl(1000, 100, CacheStrategy.LRU);
        cm.setRowCacheTTLMillis(5);
        tree.setCacheManager(cm);
        tree.put("k","v");
        Assert.assertEquals("v", tree.get("k"));
        Thread.sleep(20);
        Assert.assertEquals("v", tree.get("k"));
        tree.close();
    }
}