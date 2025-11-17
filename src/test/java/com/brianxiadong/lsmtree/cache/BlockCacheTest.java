package com.brianxiadong.lsmtree.cache;

import com.brianxiadong.lsmtree.KeyValue;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class BlockCacheTest {
    @Test
    public void testPopulateBlockForKeysAndGet() throws Exception {
        CacheManagerImpl cm = new CacheManagerImpl(10, 10, CacheStrategy.LRU);
        List<KeyValue> list = new ArrayList<>();
        list.add(new KeyValue("k01","v1"));
        list.add(new KeyValue("k02","v2"));
        list.add(new KeyValue("k03","v3"));
        cm.populateBlockForKeys(list);
        String bid = cm.computeBlockId("k02");
        Object obj = cm.get(bid, CacheType.BLOCK);
        Assert.assertTrue(obj instanceof Block);
        Block b = (Block) obj;
        Assert.assertTrue(b.getEntries().containsKey("k02"));
    }

    @Test
    public void testBlockInvalidate() throws Exception {
        CacheManagerImpl cm = new CacheManagerImpl(10, 10, CacheStrategy.LRU);
        List<KeyValue> list = new ArrayList<>();
        list.add(new KeyValue("k01","v1"));
        list.add(new KeyValue("k02","v2"));
        cm.populateBlockForKeys(list);
        String bid = cm.computeBlockId("k02");
        Object obj = cm.get(bid, CacheType.BLOCK);
        Assert.assertNotNull(obj);
        cm.invalidate(bid, CacheType.BLOCK);
        Assert.assertNull(cm.get(bid, CacheType.BLOCK));
    }
}