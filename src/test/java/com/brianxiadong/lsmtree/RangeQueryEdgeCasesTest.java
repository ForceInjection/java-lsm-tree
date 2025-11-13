package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RangeQueryEdgeCasesTest {
    @Test(expected = IllegalArgumentException.class)
    public void testStartGreaterThanEnd() throws Exception {
        LSMTree tree = new LSMTree(TestConfig.getFunctionalTestDataPath("range-edge1"), 10);
        try {
            tree.range("b", "a", true, true);
        } finally {
            tree.close();
        }
    }

    @Test
    public void testNullStartOrEnd() throws Exception {
        LSMTree tree = new LSMTree(TestConfig.getFunctionalTestDataPath("range-edge2"), 100);
        for (int i = 1; i <= 5; i++) tree.put("a" + i, "v" + i);

        Iterator<KeyValue> it1 = tree.range(null, "a3", true, true);
        List<String> k1 = new ArrayList<>();
        while (it1.hasNext()) k1.add(it1.next().getKey());
        Assert.assertArrayEquals(new String[]{"a1","a2","a3"}, k1.toArray(new String[0]));

        Iterator<KeyValue> it2 = tree.range("a3", null, true, true);
        List<String> k2 = new ArrayList<>();
        while (it2.hasNext()) k2.add(it2.next().getKey());
        Assert.assertArrayEquals(new String[]{"a3","a4","a5"}, k2.toArray(new String[0]));

        tree.close();
    }
}

