package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class RangePartitionBoundaryTest {
    @Test
    public void testGetPartitionAndRangePartitions() {
        RangePartitionStrategy strat = new RangePartitionStrategy(Arrays.asList("b","d"));
        Assert.assertEquals(0, strat.getPartition("a", 3));
        Assert.assertEquals(0, strat.getPartition("b", 3));
        Assert.assertEquals(1, strat.getPartition("c", 3));
        Assert.assertEquals(1, strat.getPartition("d", 3));
        Assert.assertEquals(2, strat.getPartition("e", 3));
        List<Integer> parts1 = strat.getPartitionsForRange("a","c",3);
        Assert.assertEquals(Arrays.asList(0,1), parts1);
        List<Integer> parts2 = strat.getPartitionsForRange(null,"a",3);
        Assert.assertEquals(Arrays.asList(0), parts2);
        List<Integer> parts3 = strat.getPartitionsForRange("e",null,3);
        Assert.assertEquals(Arrays.asList(2), parts3);
    }

    @Test
    public void testPartitionedRangeQuery() throws Exception {
        RangePartitionStrategy strat = new RangePartitionStrategy(Arrays.asList("b","d"));
        try (PartitionedLSMTree db = new PartitionedLSMTree(TestConfig.getFunctionalTestDataPath("part-range"), 3, 10, strat)) {
            for (String k : Arrays.asList("a","b","c","d","e")) db.put(k, k);
            Iterator<KeyValue> it = db.range("a","e", true, true);
            StringBuilder sb = new StringBuilder();
            while (it.hasNext()) sb.append(it.next().getKey());
            Assert.assertEquals("abcde", sb.toString());
        }
    }
}

