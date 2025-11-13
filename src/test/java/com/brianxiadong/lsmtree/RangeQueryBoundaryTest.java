package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RangeQueryBoundaryTest {
    @Test
    public void testInclusiveExclusiveBoundaries() throws Exception {
        LSMTree tree = new LSMTree(TestConfig.getFunctionalTestDataPath("range-boundary"), 100);
        for (int i = 1; i <= 5; i++) tree.put("a" + i, "v" + i);
        Iterator<KeyValue> it1 = tree.range("a2", "a4", true, true);
        List<String> k1 = new ArrayList<>();
        while (it1.hasNext()) k1.add(it1.next().getKey());
        Assert.assertArrayEquals(new String[]{"a2","a3","a4"}, k1.toArray(new String[0]));
        Iterator<KeyValue> it2 = tree.range("a2", "a4", false, true);
        List<String> k2 = new ArrayList<>();
        while (it2.hasNext()) k2.add(it2.next().getKey());
        Assert.assertArrayEquals(new String[]{"a3","a4"}, k2.toArray(new String[0]));
        Iterator<KeyValue> it3 = tree.range("a2", "a4", true, false);
        List<String> k3 = new ArrayList<>();
        while (it3.hasNext()) k3.add(it3.next().getKey());
        Assert.assertArrayEquals(new String[]{"a2","a3"}, k3.toArray(new String[0]));
        Iterator<KeyValue> it4 = tree.range("a2", "a4", false, false);
        List<String> k4 = new ArrayList<>();
        while (it4.hasNext()) k4.add(it4.next().getKey());
        Assert.assertArrayEquals(new String[]{"a3"}, k4.toArray(new String[0]));
        tree.close();
    }

    @Test
    public void testEmptyAndFullRanges() throws Exception {
        LSMTree tree = new LSMTree(TestConfig.getFunctionalTestDataPath("range-empty-full"), 100);
        for (int i = 1; i <= 3; i++) tree.put("a" + i, "v" + i);
        Iterator<KeyValue> it1 = tree.range("b1", "b2", true, true);
        Assert.assertFalse(it1.hasNext());
        Iterator<KeyValue> it2 = tree.range(null, null, true, true);
        List<String> all = new ArrayList<>();
        while (it2.hasNext()) all.add(it2.next().getKey());
        Assert.assertArrayEquals(new String[]{"a1","a2","a3"}, all.toArray(new String[0]));
        tree.close();
    }

    @Test
    public void testTombstoneVisibilityInRange() throws Exception {
        LSMTree tree = new LSMTree(TestConfig.getFunctionalTestDataPath("range-tombstone"), 100);
        tree.put("a1","v1");
        tree.put("a2","v2");
        tree.delete("a2");
        Iterator<KeyValue> it = tree.range("a1","a3", true, true);
        List<String> keys = new ArrayList<>();
        while (it.hasNext()) keys.add(it.next().getKey());
        Assert.assertArrayEquals(new String[]{"a1"}, keys.toArray(new String[0]));
        tree.close();
    }
}

