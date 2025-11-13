package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class CompressionStrategyTest {
    @Test
    public void testLZ4RoundTrip() throws Exception {
        String dir = Files.createTempDirectory("sst-lz4").toFile().getAbsolutePath();
        String file = dir + "/sstable_level0_" + System.currentTimeMillis() + ".db";
        List<KeyValue> data = new ArrayList<>();
        for (int i = 0; i < 100; i++) data.add(new KeyValue("k"+i, "v"+i));
        data.sort(KeyValue::compareTo);
        CompressionStrategy lz4 = new LZ4CompressionStrategy();
        SSTable t = new SSTable(file, data, lz4);
        for (int i = 0; i < 100; i++) {
            String v = t.get("k"+i);
            Assert.assertEquals("v"+i, v);
        }
    }
}

