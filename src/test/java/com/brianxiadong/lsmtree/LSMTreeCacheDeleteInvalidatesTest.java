package com.brianxiadong.lsmtree;

import com.brianxiadong.lsmtree.cache.CacheManagerImpl;
import com.brianxiadong.lsmtree.cache.CacheStrategy;
import com.brianxiadong.lsmtree.cache.CacheType;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class LSMTreeCacheDeleteInvalidatesTest {
    @Test
    public void testDeleteReflectsInCacheAndGetReturnsNull() throws Exception {
        String dir = System.getProperty("java.io.tmpdir") + File.separator + "cache_del_" + System.currentTimeMillis();
        LSMTree tree = new LSMTree(dir, 100);
        CacheManagerImpl cm = new CacheManagerImpl(1000, 100, CacheStrategy.LRU);
        tree.setCacheManager(cm);
        tree.put("k","v");
        Assert.assertEquals("v", tree.get("k"));
        tree.delete("k");
        Assert.assertNull(tree.get("k"));
        Object obj = cm.get("k", CacheType.ROW);
        Assert.assertTrue(obj instanceof KeyValue);
        Assert.assertTrue(((KeyValue) obj).isDeleted());
        tree.close();
    }
}