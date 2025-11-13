package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

public class MemTableShouldFlushTest {
    @Test
    public void testShouldFlushAtThreshold() {
        MemTable mt = new MemTable(3);
        mt.put("k1","v1");
        mt.put("k2","v2");
        Assert.assertFalse(mt.shouldFlush());
        mt.put("k3","v3");
        Assert.assertTrue(mt.shouldFlush());
    }
}

