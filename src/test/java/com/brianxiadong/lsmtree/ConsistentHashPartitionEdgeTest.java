package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.Iterator;

public class ConsistentHashPartitionEdgeTest {
    @Test
    public void testGetPartitionsForRangeReturnsAll() {
        ConsistentHashPartitionStrategy strat = new ConsistentHashPartitionStrategy();
        Assert.assertEquals(4, strat.getPartitionsForRange("a","z",4).size());
    }

    @Test
    public void testStablePartitionMapping() {
        ConsistentHashPartitionStrategy strat = new ConsistentHashPartitionStrategy();
        int p1 = strat.getPartition("key-123", 8);
        int p2 = strat.getPartition("key-123", 8);
        Assert.assertEquals(p1, p2);
        Assert.assertTrue(p1 >= 0 && p1 < 8);
    }

    @Test
    public void testPartitionedOperations() throws Exception {
        ConsistentHashPartitionStrategy strat = new ConsistentHashPartitionStrategy();
        try (PartitionedLSMTree db = new PartitionedLSMTree(TestConfig.getFunctionalTestDataPath("hash-part"), 4, 10, strat)) {
            db.put("u1","v1");
            db.put("u2","v2");
            Assert.assertEquals("v1", db.get("u1"));
            Assert.assertEquals("v2", db.get("u2"));
            Iterator<KeyValue> it = db.range(null, null, true, true);
            int c = 0;
            while (it.hasNext()) { it.next(); c++; }
            Assert.assertTrue(c >= 2);
        }
    }
}

