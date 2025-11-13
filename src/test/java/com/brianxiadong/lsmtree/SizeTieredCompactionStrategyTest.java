package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class SizeTieredCompactionStrategyTest {
    @Test
    public void testNeedsAndCompact() throws Exception {
        String dir = Files.createTempDirectory("lsm-size-tier").toFile().getAbsolutePath();
        SizeTieredCompactionStrategy strategy = new SizeTieredCompactionStrategy(dir, 1024, 4);

        List<SSTable> tables = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            List<KeyValue> entries = new ArrayList<>();
            for (int k = 0; k < 100; k++) entries.add(new KeyValue("k" + k, "v" + i));
            String file = String.format("%s/sstable_level0_%d_%d.db", dir, System.currentTimeMillis(), i);
            tables.add(new SSTable(file, entries));
        }

        Assert.assertTrue(strategy.needsCompaction(tables));
        List<SSTable> after = strategy.compact(tables);
        Assert.assertTrue(after.size() < tables.size());
    }

    @Test
    public void testSelectTask() throws IOException {
        String dir = Files.createTempDirectory("lsm-size-tier-2").toFile().getAbsolutePath();
        SizeTieredCompactionStrategy strategy = new SizeTieredCompactionStrategy(dir, 1024, 3);
        List<SSTable> tables = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            List<KeyValue> entries = new ArrayList<>();
            for (int k = 0; k < 10; k++) entries.add(new KeyValue("k" + k, "v" + i));
            String file = String.format("%s/sstable_level0_%d_%d.db", dir, System.currentTimeMillis(), i);
            tables.add(new SSTable(file, entries));
        }
        LeveledCompactionStrategy.CompactionTask task = strategy.selectCompactionTask(tables);
        Assert.assertNotNull(task);
    }
}

