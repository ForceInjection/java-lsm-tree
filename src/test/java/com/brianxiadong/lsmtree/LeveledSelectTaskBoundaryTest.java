package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class LeveledSelectTaskBoundaryTest {
    @Test
    public void testSelectTaskReturnsNullWhenBelowThreshold() throws Exception {
        String dir = Files.createTempDirectory("lsm-level-select").toFile().getAbsolutePath();
        LeveledCompactionStrategy strategy = new LeveledCompactionStrategy(dir, 4, 10);
        List<SSTable> level0 = new ArrayList<>();
        // 构造等于阈值的 level0 文件数（maxLevelSize=4）
        for (int i = 0; i < 4; i++) {
            List<KeyValue> entries = new ArrayList<>();
            for (int k = 0; k < 10; k++) entries.add(new KeyValue("k"+k, "v"+i));
            String file = String.format("%s/sstable_level0_%d_%d.db", dir, System.currentTimeMillis(), i);
            level0.add(new SSTable(file, entries));
        }
        // 增加一张表，超过阈值，应返回任务
        List<KeyValue> extra = new ArrayList<>();
        for (int k = 0; k < 10; k++) extra.add(new KeyValue("k"+k, "vX"));
        String file = String.format("%s/sstable_level0_%d_%d.db", dir, System.currentTimeMillis(), 99);
        level0.add(new SSTable(file, extra));
        LeveledCompactionStrategy.CompactionTask task2 = strategy.selectCompactionTask(level0);
        Assert.assertNotNull(task2);
    }
}
