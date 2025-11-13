package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class MemTableRangeTest {
    @Test
    public void testIncludeFlagsAndDeletes() {
        MemTable mt = new MemTable(100);
        mt.put("a1","v1");
        mt.put("a2","v2");
        mt.put("a3","v3");
        mt.delete("a2");

        List<KeyValue> inc = mt.getRange("a1","a3", true, true);
        Assert.assertEquals(2, inc.size());
        Assert.assertEquals("a1", inc.get(0).getKey());
        Assert.assertEquals("a3", inc.get(1).getKey());

        List<KeyValue> exc = mt.getRange("a1","a3", false, false);
        Assert.assertEquals(0, exc.size());
    }
}
