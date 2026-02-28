package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Size-Tiered压缩任务选择测试类
 * 测试无合适tier时返回null
 */
public class SizeTieredSelectTaskNoneTest {
    @Test
    public void testNoTierMeetsThresholdReturnsNull() throws Exception {
        TestLogger log = new TestLogger("Size-Tiered无合适tier测试");
        log.start("测试没有tier满足阈值时返回null");
        
        String dir = Files.createTempDirectory("lsm-size-tier-none").toFile().getAbsolutePath();
        SizeTieredCompactionStrategy strategy = new SizeTieredCompactionStrategy(dir, 1 << 10, 5);
        log.data("size阈值", "1024 bytes");
        log.data("minFiles阈值", 5);
        
        log.step("创建4个SSTable（低于minFiles阈值）");
        List<SSTable> tables = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            List<KeyValue> entries = new ArrayList<>();
            for (int k = 0; k < 5; k++) entries.add(new KeyValue("k"+k, "v"+i));
            String file = String.format("%s/sstable_level0_%d_%d.db", dir, System.currentTimeMillis(), i);
            tables.add(new SSTable(file, entries));
        }
        log.data("SSTable数量", 4);
        log.data("minFiles阈值", 5);
        
        log.step("选择压缩任务");
        LeveledCompactionStrategy.CompactionTask task = strategy.selectCompactionTask(tables);
        log.data("任务结果", task == null ? "null（无压缩）" : "非null");
        
        Assert.assertNull(task);
        log.assertSuccess("SSTable数量不足minFiles，返回null");
        log.pass();
    }
}

