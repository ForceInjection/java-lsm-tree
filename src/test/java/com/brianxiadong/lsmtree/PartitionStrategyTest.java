package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class PartitionStrategyTest {
    @Test
    public void testRangePartitionMapping() {
        List<String> bounds = Arrays.asList("b","d","f");
        RangePartitionStrategy s = new RangePartitionStrategy(bounds);
        Assert.assertEquals(0, s.getPartition("a", 4));
        Assert.assertEquals(1, s.getPartition("c", 4));
        Assert.assertEquals(2, s.getPartition("e", 4));
        Assert.assertEquals(3, s.getPartition("z", 4));
        List<Integer> ps = s.getPartitionsForRange("a","e",4);
        Assert.assertEquals(Arrays.asList(0,1,2), ps);
    }

    @Test
    public void testConsistentHashCoversAll() {
        ConsistentHashPartitionStrategy s = new ConsistentHashPartitionStrategy();
        List<Integer> ps = s.getPartitionsForRange("a","z", 8);
        Assert.assertEquals(8, ps.size());
        for (int i = 0; i < 8; i++) Assert.assertTrue(ps.contains(i));
    }
}

