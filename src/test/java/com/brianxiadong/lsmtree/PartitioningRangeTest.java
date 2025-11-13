package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class PartitioningRangeTest {
    @Test
    public void testRangeAcrossPartitions() throws Exception {
        List<String> bounds = Arrays.asList("b","d","f"); // 4 partitions: (-,b],[b,d],[d,f],[f,+)
        PartitionStrategy s = new RangePartitionStrategy(bounds);
        try (PartitionedLSMTree tree = new PartitionedLSMTree(TestConfig.getFunctionalTestDataPath("partitioning"), 4, 10, s)) {
            for (char c = 'a'; c <= 'h'; c++) {
                tree.put(""+c, "v"+c);
            }
            Iterator<KeyValue> it = tree.range("a","h", true, true);
            int count = 0;
            while (it.hasNext()) {
                it.next();
                count++;
            }
            Assert.assertEquals(8, count);
        }
    }
}

