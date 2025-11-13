package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RangeQueryLayeringTest {
    @Test
    public void testAcrossMemtableAndSSTable() throws Exception {
        LSMTree tree = new LSMTree(TestConfig.getFunctionalTestDataPath("range-layering"), 3);
        tree.put("a1","v1");
        tree.put("a2","v2");
        tree.put("a3","v3");
        tree.put("a4","v4");
        tree.put("a5","v5");
        Iterator<KeyValue> it = tree.range("a1","a5", true, true);
        List<String> keys = new ArrayList<>();
        while (it.hasNext()) keys.add(it.next().getKey());
        Assert.assertArrayEquals(new String[]{"a1","a2","a3","a4","a5"}, keys.toArray(new String[0]));
        tree.close();
    }

    @Test
    public void testLatestVersionWins() throws Exception {
        LSMTree tree = new LSMTree(TestConfig.getFunctionalTestDataPath("range-latest"), 2);
        tree.put("a1","v1");
        tree.put("a2","v2");
        tree.put("a1","v1_new");
        Iterator<KeyValue> it = tree.range("a1","a2", true, true);
        List<String> vals = new ArrayList<>();
        while (it.hasNext()) vals.add(it.next().getValue());
        Assert.assertArrayEquals(new String[]{"v1_new","v2"}, vals.toArray(new String[0]));
        tree.close();
    }
}

