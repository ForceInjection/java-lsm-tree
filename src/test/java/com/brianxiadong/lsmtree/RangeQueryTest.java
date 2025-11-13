package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RangeQueryTest {
    @Test
    public void testRangeInclusive() throws Exception {
        String dir = TestConfig.getFunctionalTestDataPath("range1");
        deleteDir(dir);
        LSMTree tree = new LSMTree(dir, 3);
        tree.put("a1", "v1");
        tree.put("a2", "v2");
        tree.put("a3", "v3");
        tree.put("a4", "v4");
        tree.put("a5", "v5");
        Iterator<KeyValue> it = tree.range("a2", "a4", true, true);
        List<String> keys = new ArrayList<>();
        while (it.hasNext()) keys.add(it.next().getKey());
        Assert.assertEquals(3, keys.size());
        Assert.assertArrayEquals(new String[]{"a2","a3","a4"}, keys.toArray(new String[0]));
        tree.close();
    }

    @Test
    public void testRangeExclusiveAndReverse() throws Exception {
        String dir = TestConfig.getFunctionalTestDataPath("range2");
        deleteDir(dir);
        LSMTree tree = new LSMTree(dir, 2);
        tree.put("a1", "v1");
        tree.put("a2", "v2");
        tree.put("a3", "v3");
        tree.put("a4", "v4");
        tree.put("a5", "v5");
        Iterator<KeyValue> it = tree.range("a2", "a4", false, false);
        List<String> keys = new ArrayList<>();
        while (it.hasNext()) keys.add(it.next().getKey());
        Assert.assertEquals(1, keys.size());
        Assert.assertEquals("a3", keys.get(0));
        Iterator<KeyValue> rev = tree.rangeReverse("a2", "a4");
        List<String> rkeys = new ArrayList<>();
        while (rev.hasNext()) rkeys.add(rev.next().getKey());
        Assert.assertArrayEquals(new String[]{"a4","a3","a2"}, rkeys.toArray(new String[0]));
        tree.close();
    }

    @Test
    public void testRangeWithDeletes() throws Exception {
        String dir = TestConfig.getFunctionalTestDataPath("range3");
        deleteDir(dir);
        LSMTree tree = new LSMTree(dir, 10);
        tree.put("a1", "v1");
        tree.put("a2", "v2");
        tree.put("a3", "v3");
        tree.delete("a2");
        Iterator<KeyValue> it = tree.range("a1", "a3", true, true);
        List<String> keys = new ArrayList<>();
        while (it.hasNext()) keys.add(it.next().getKey());
        Assert.assertArrayEquals(new String[]{"a1","a3"}, keys.toArray(new String[0]));
        tree.close();
    }

    private void deleteDir(String path) {
        File f = new File(path);
        if (!f.exists()) return;
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null) {
                for (File c : files) c.delete();
            }
        }
        f.delete();
    }
}

