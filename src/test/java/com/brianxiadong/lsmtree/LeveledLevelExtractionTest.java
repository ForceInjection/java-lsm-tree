package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class LeveledLevelExtractionTest {
    @Test
    public void testUnknownFilenameDefaultsToLevel0() throws Exception {
        String dir = Files.createTempDirectory("lsm-level-extract").toFile().getAbsolutePath();
        String file = dir + "/abcd_" + System.currentTimeMillis() + ".db";
        List<KeyValue> data = new ArrayList<>();
        data.add(new KeyValue("a","v"));
        SSTable t = new SSTable(file, data);
        List<SSTable> list = new ArrayList<>();
        list.add(t);
        LeveledCompactionStrategy s = new LeveledCompactionStrategy(dir, 4, 10);
        LeveledCompactionStrategy.CompactionTask task = s.selectCompactionTask(list);
        Assert.assertNotNull(task);
    }
}
