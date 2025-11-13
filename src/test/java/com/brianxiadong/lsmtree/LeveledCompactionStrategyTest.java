package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class LeveledCompactionStrategyTest {
    @Test
    public void testNeedsAndCompact() throws Exception {
        String dir = Files.createTempDirectory("lsm-compact").toFile().getAbsolutePath();
        LeveledCompactionStrategy strategy = new LeveledCompactionStrategy(dir, 4, 10);

        List<SSTable> level0 = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            List<KeyValue> entries = new ArrayList<>();
            for (int k = 0; k < 100; k++) {
                entries.add(new KeyValue("k" + k, "v" + i));
            }
            String file = String.format("%s/sstable_level0_%d_%d.db", dir, System.currentTimeMillis(), i);
            level0.add(new SSTable(file, entries));
        }

        Assert.assertTrue(strategy.needsCompaction(level0));
        List<SSTable> after = strategy.compact(level0);
        Assert.assertFalse(after.isEmpty());
        boolean allNextLevel = after.stream().allMatch(t -> t.getFilePath().contains("level1"));
        Assert.assertTrue(allNextLevel);
    }

    @Test
    public void testCompactOnEmpty() throws IOException {
        String dir = Files.createTempDirectory("lsm-compact-empty").toFile().getAbsolutePath();
        LeveledCompactionStrategy strategy = new LeveledCompactionStrategy(dir, 4, 10);
        List<SSTable> res = strategy.compact(new ArrayList<>());
        Assert.assertTrue(res.isEmpty());
    }
}

