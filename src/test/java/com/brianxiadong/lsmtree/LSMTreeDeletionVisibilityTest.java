package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class LSMTreeDeletionVisibilityTest {
    @Test
    public void testDeletionAcrossLayersInRangeAndGet() throws Exception {
        String dir = TestConfig.getFunctionalTestDataPath("del-vis");
        LSMTree tree = new LSMTree(dir, 3);
        tree.put("k1","v1");
        tree.put("k2","v2");
        tree.put("k3","v3"); // 添加 k3 键
        tree.put("kX","vx"); // reach threshold and flush to level0
        tree.delete("k2");     // tombstone in active memtable, not flushed

        Assert.assertNull(tree.get("k2"));
        Iterator<KeyValue> it = tree.range("k1","kZ", true, true);
        List<String> keys = new ArrayList<>();
        while (it.hasNext()) keys.add(it.next().getKey());
        Assert.assertFalse(keys.contains("k2"));
        Assert.assertTrue(keys.contains("k1"));
        Assert.assertTrue(keys.contains("k3"));
        tree.close();
    }
}
