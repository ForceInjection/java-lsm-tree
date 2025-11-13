package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class SizeTieredSelectTaskNoneTest {
    @Test
    public void testNoTierMeetsThresholdReturnsNull() throws Exception {
        String dir = Files.createTempDirectory("lsm-size-tier-none").toFile().getAbsolutePath();
        SizeTieredCompactionStrategy strategy = new SizeTieredCompactionStrategy(dir, 1 << 10, 5);
        List<SSTable> tables = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            List<KeyValue> entries = new ArrayList<>();
            for (int k = 0; k < 5; k++) entries.add(new KeyValue("k"+k, "v"+i));
            String file = String.format("%s/sstable_level0_%d_%d.db", dir, System.currentTimeMillis(), i);
            tables.add(new SSTable(file, entries));
        }
        LeveledCompactionStrategy.CompactionTask task = strategy.selectCompactionTask(tables);
        Assert.assertNull(task);
    }
}

