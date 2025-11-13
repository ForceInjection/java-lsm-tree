package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RangeReverseOrderTest {
    @Test
    public void testReverseOrderAndDeletes() throws Exception {
        LSMTree tree = new LSMTree(TestConfig.getFunctionalTestDataPath("range-reverse"), 5);
        for (int i = 1; i <= 5; i++) tree.put("a" + i, "v" + i);
        tree.delete("a3");
        Iterator<KeyValue> it = tree.rangeReverse("a2","a5");
        List<String> keys = new ArrayList<>();
        while (it.hasNext()) keys.add(it.next().getKey());
        Assert.assertArrayEquals(new String[]{"a5","a4","a2"}, keys.toArray(new String[0]));
        tree.close();
    }
}

