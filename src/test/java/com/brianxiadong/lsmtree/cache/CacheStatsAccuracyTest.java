package com.brianxiadong.lsmtree.cache;

import com.brianxiadong.lsmtree.KeyValue;
import org.junit.Assert;
import org.junit.Test;

public class CacheStatsAccuracyTest {
    @Test
    public void testHitMissEvictCounters() throws Exception {
        CacheManagerImpl cm = new CacheManagerImpl(2, 1, CacheStrategy.LRU);
        Assert.assertEquals(0, cm.getStats(CacheType.ROW).getHits());
        Assert.assertEquals(0, cm.getStats(CacheType.ROW).getMisses());
        Assert.assertNull(cm.get("a", CacheType.ROW));
        cm.put("a", new KeyValue("a","1"), CacheType.ROW);
        cm.put("b", new KeyValue("b","2"), CacheType.ROW);
        Assert.assertNotNull(cm.get("a", CacheType.ROW));
        cm.put("c", new KeyValue("c","3"), CacheType.ROW);
        long hits = cm.getStats(CacheType.ROW).getHits();
        long misses = cm.getStats(CacheType.ROW).getMisses();
        long evicts = cm.getStats(CacheType.ROW).getEvictions();
        Assert.assertTrue(hits >= 1);
        Assert.assertTrue(misses >= 1);
        Assert.assertTrue(evicts >= 1);
    }
}