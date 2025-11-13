package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.Iterator;

public class RangeQueryVersionSelectionAcrossLayersTest {
    @Test
    public void testLatestAcrossSSTableAndMemtable() throws Exception {
        LSMTree tree = new LSMTree(TestConfig.getFunctionalTestDataPath("range-version"), 2);
        // 先写入并触发刷盘，使旧值进入 SSTable
        tree.put("k","v1");
        tree.put("x","dummy"); // 触发 flush
        tree.flush();
        // 再写入新值，保留在 memtable
        tree.put("k","v2");

        Iterator<KeyValue> it = tree.range("k","k", true, true);
        Assert.assertTrue(it.hasNext());
        KeyValue kv = it.next();
        Assert.assertEquals("k", kv.getKey());
        Assert.assertEquals("v2", kv.getValue());
        Assert.assertFalse(it.hasNext());
        tree.close();
    }
}

