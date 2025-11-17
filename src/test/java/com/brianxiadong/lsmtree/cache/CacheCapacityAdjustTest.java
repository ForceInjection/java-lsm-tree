package com.brianxiadong.lsmtree.cache;

import com.brianxiadong.lsmtree.KeyValue;
import org.junit.Assert;
import org.junit.Test;

public class CacheCapacityAdjustTest {
    @Test
    public void testAdjustCapacityDoesNotBreakService() throws Exception {
        CacheManagerImpl cm = new CacheManagerImpl(3, 1, CacheStrategy.LRU);
        cm.put("a", new KeyValue("a","1", System.currentTimeMillis(), false), CacheType.ROW);
        cm.put("b", new KeyValue("b","2", System.currentTimeMillis(), false), CacheType.ROW);
        cm.put("c", new KeyValue("c","3", System.currentTimeMillis(), false), CacheType.ROW);
        cm.adjustCapacity(CacheType.ROW, 2);
        Object oa = cm.get("a", CacheType.ROW);
        Object ob = cm.get("b", CacheType.ROW);
        Object oc = cm.get("c", CacheType.ROW);
        int alive = (oa!=null?1:0) + (ob!=null?1:0) + (oc!=null?1:0);
        Assert.assertEquals(2, alive);
        cm.adjustCapacity(CacheType.ROW, 4);
        cm.put("d", new KeyValue("d","4", System.currentTimeMillis(), false), CacheType.ROW);
        Assert.assertNotNull(cm.get("d", CacheType.ROW));
    }
}