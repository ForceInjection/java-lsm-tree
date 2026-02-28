package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Size-Tiered压缩策略测试类
 * 测试基于大小的分层压缩策略
 */
public class SizeTieredCompactionStrategyTest {
    @Test
    public void testNeedsAndCompact() throws Exception {
        TestLogger log = new TestLogger("Size-Tiered压缩触发和执行测试");
        log.start("测试Size-Tiered压缩策略的触发和执行");
        
        String dir = Files.createTempDirectory("lsm-size-tier").toFile().getAbsolutePath();
        log.data("临时目录", dir);
        
        log.step("创建SizeTieredCompactionStrategy（sizeThreshold=1024, minFiles=4）");
        SizeTieredCompactionStrategy strategy = new SizeTieredCompactionStrategy(dir, 1024, 4);

        log.step("创建6个SSTable");
        List<SSTable> tables = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            List<KeyValue> entries = new ArrayList<>();
            for (int k = 0; k < 100; k++) entries.add(new KeyValue("k" + k, "v" + i));
            String file = String.format("%s/sstable_level0_%d_%d.db", dir, System.currentTimeMillis(), i);
            tables.add(new SSTable(file, entries));
        }
        log.data("SSTable数量", tables.size());

        log.step("检查压缩触发条件");
        boolean needs = strategy.needsCompaction(tables);
        log.data("需要压缩", needs);
        Assert.assertTrue(needs);
        
        log.step("执行压缩");
        List<SSTable> after = strategy.compact(tables);
        log.data("压缩前数量", tables.size());
        log.data("压缩后数量", after.size());
        Assert.assertTrue(after.size() < tables.size());
        log.assertSuccess("压缩减少了SSTable数量");
        log.pass();
    }

    @Test
    public void testSelectTask() throws IOException {
        TestLogger log = new TestLogger("Size-Tiered任务选择测试");
        log.start("测试压缩任务的选择逻辑");
        
        String dir = Files.createTempDirectory("lsm-size-tier-2").toFile().getAbsolutePath();
        SizeTieredCompactionStrategy strategy = new SizeTieredCompactionStrategy(dir, 1024, 3);
        
        log.step("创建3个SSTable（达到最小合并阈值）");
        List<SSTable> tables = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            List<KeyValue> entries = new ArrayList<>();
            for (int k = 0; k < 10; k++) entries.add(new KeyValue("k" + k, "v" + i));
            String file = String.format("%s/sstable_level0_%d_%d.db", dir, System.currentTimeMillis(), i);
            tables.add(new SSTable(file, entries));
        }
        log.data("SSTable数量", tables.size());
        
        log.step("选择压缩任务");
        LeveledCompactionStrategy.CompactionTask task = strategy.selectCompactionTask(tables);
        log.data("任务是否为null", task == null);
        Assert.assertNotNull(task);
        log.assertSuccess("成功选择了压缩任务");
        log.pass();
    }
}
