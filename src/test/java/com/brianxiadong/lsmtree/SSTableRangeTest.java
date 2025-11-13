package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class SSTableRangeTest {
    @Test
    public void testRangeExclusive() throws Exception {
        String dir = Files.createTempDirectory("sst-range").toFile().getAbsolutePath();
        String file = dir + "/sstable_level0_" + System.currentTimeMillis() + ".db";
        List<KeyValue> data = new ArrayList<>();
        data.add(new KeyValue("a1","v1"));
        data.add(new KeyValue("a2","v2"));
        data.add(new KeyValue("a3","v3"));
        data.sort(KeyValue::compareTo);
        SSTable t = new SSTable(file, data);
        List<KeyValue> res = t.getRangeEntries("a1","a3", false, false);
        Assert.assertEquals(1, res.size());
        Assert.assertEquals("a2", res.get(0).getKey());
    }

    @Test
    public void testEmptySSTableRange() throws IOException {
        String dir = Files.createTempDirectory("sst-range-empty").toFile().getAbsolutePath();
        String file = dir + "/sstable_level0_" + System.currentTimeMillis() + ".db";
        List<KeyValue> data = new ArrayList<>();
        SSTable t = new SSTable(file, data);
        List<KeyValue> res = t.getRangeEntries("a","z", true, true);
        Assert.assertTrue(res.isEmpty());
    }
}

