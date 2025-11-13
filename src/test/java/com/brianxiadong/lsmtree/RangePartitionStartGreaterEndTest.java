package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class RangePartitionStartGreaterEndTest {
    @Test
    public void testStartGreaterThanEndPartitionsAscending() {
        RangePartitionStrategy strat = new RangePartitionStrategy(Arrays.asList("m","t"));
        // start > end 时应返回从小到大的分区区间
        Assert.assertEquals(Arrays.asList(0,1,2), strat.getPartitionsForRange("z","a",3));
    }
}
