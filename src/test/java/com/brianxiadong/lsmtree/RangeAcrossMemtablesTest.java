package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RangeAcrossMemtablesTest {
    @Test
    public void testRangeSpanningImmutableAndActive() throws Exception {
        LSMTree tree = new LSMTree(TestConfig.getFunctionalTestDataPath("range-immut"), 2);
        tree.put("a1","v1");
        tree.put("a2","v2"); // flush to level0
        tree.put("a3","v3"); // active
        Iterator<KeyValue> it = tree.range("a1","a3", true, true);
        List<String> keys = new ArrayList<>();
        while (it.hasNext()) keys.add(it.next().getKey());
        Assert.assertArrayEquals(new String[]{"a1","a2","a3"}, keys.toArray(new String[0]));
        tree.close();
    }
}

