package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class SSTableBloomFilterMissTest {
    @Test
    public void testGetNonExistingKeyReturnsNull() throws Exception {
        String dir = Files.createTempDirectory("sst-bloom-miss").toFile().getAbsolutePath();
        String file = dir + "/sstable_level0_" + System.currentTimeMillis() + ".db";
        List<KeyValue> data = new ArrayList<>();
        for (int i = 0; i < 100; i++) data.add(new KeyValue("k"+i, "v"+i));
        data.sort(KeyValue::compareTo);
        SSTable t = new SSTable(file, data);
        String val = t.get("ZZZ_non_exist_key");
        Assert.assertNull(val);
    }
}

