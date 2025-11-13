package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class LeveledCompactionNoOpTest {
    @Test
    public void testSelectTaskReturnsNullWhenBelowThreshold() throws Exception {
        String dir = Files.createTempDirectory("lsm-level-noop").toFile().getAbsolutePath();
        LeveledCompactionStrategy s = new LeveledCompactionStrategy(dir, 10, 10);
        List<SSTable> list = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            List<KeyValue> data = new ArrayList<>();
            data.add(new KeyValue("a"+i, "v"+i));
            String file = String.format("%s/sstable_level0_%d_%d.db", dir, System.currentTimeMillis(), i);
            list.add(new SSTable(file, data));
        }
        LeveledCompactionStrategy.CompactionTask t = s.selectCompactionTask(list);
        Assert.assertNull(t);
    }
}

