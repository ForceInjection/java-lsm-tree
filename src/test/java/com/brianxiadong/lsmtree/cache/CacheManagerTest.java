package com.brianxiadong.lsmtree.cache;

import com.brianxiadong.lsmtree.KeyValue;
import org.junit.Assert;
import org.junit.Test;

public class CacheManagerTest {
    @Test
    public void testBasicPutGetInvalidateLRU() throws Exception {
        CacheManagerImpl cm = new CacheManagerImpl(2, 2, CacheStrategy.LRU);
        KeyValue kv1 = new KeyValue("k1","v1", System.currentTimeMillis(), false);
        KeyValue kv2 = new KeyValue("k2","v2", System.currentTimeMillis(), false);
        cm.put("k1", kv1, CacheType.ROW);
        cm.put("k2", kv2, CacheType.ROW);
        Assert.assertNotNull(cm.get("k1", CacheType.ROW));
        Assert.assertNotNull(cm.get("k2", CacheType.ROW));
        cm.put("k3", new KeyValue("k3","v3", System.currentTimeMillis(), false), CacheType.ROW);
        cm.get("k3", CacheType.ROW);
        CacheStats stats = cm.getStats(CacheType.ROW);
        Assert.assertTrue(stats.getEvictions() >= 1);
        cm.invalidate("k3", CacheType.ROW);
        Assert.assertNull(cm.get("k3", CacheType.ROW));
    }

    @Test
    public void testLFUBehavior() throws Exception {
        CacheManagerImpl cm = new CacheManagerImpl(2, 2, CacheStrategy.LFU);
        cm.put("a", new KeyValue("a","1", System.currentTimeMillis(), false), CacheType.ROW);
        cm.put("b", new KeyValue("b","2", System.currentTimeMillis(), false), CacheType.ROW);
        cm.get("a", CacheType.ROW);
        cm.put("c", new KeyValue("c","3", System.currentTimeMillis(), false), CacheType.ROW);
        Object oa = cm.get("a", CacheType.ROW);
        Object ob = cm.get("b", CacheType.ROW);
        Object oc = cm.get("c", CacheType.ROW);
        Assert.assertNotNull(oa);
        int alive = (oa!=null?1:0) + (ob!=null?1:0) + (oc!=null?1:0);
        Assert.assertEquals(2, alive);
    }

    @Test
    public void testTTLExpiry() throws Exception {
        CacheManagerImpl cm = new CacheManagerImpl(10, 10, CacheStrategy.LRU);
        cm.setRowCacheTTLMillis(5);
        cm.put("x", new KeyValue("x","vx", System.currentTimeMillis(), false), CacheType.ROW);
        Thread.sleep(20);
        Assert.assertNull(cm.get("x", CacheType.ROW));
    }
}