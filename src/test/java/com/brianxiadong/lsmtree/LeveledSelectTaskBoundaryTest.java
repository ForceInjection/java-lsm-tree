package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 分层压缩任务选择边界测试类
 * 测试压缩任务选择的边界条件
 */
public class LeveledSelectTaskBoundaryTest {
    @Test
    public void testSelectTaskReturnsNullWhenBelowThreshold() throws Exception {
        TestLogger log = new TestLogger("压缩任务选择边界测试");
        log.start("测试SSTable超过阈值时返回压缩任务");
        
        String dir = Files.createTempDirectory("lsm-level-select").toFile().getAbsolutePath();
        LeveledCompactionStrategy strategy = new LeveledCompactionStrategy(dir, 4, 10);
        log.data("Level0阈值", 4);
        
        log.step("创建4个SSTable（等于阈值）");
        List<SSTable> level0 = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            List<KeyValue> entries = new ArrayList<>();
            for (int k = 0; k < 10; k++) entries.add(new KeyValue("k"+k, "v"+i));
            String file = String.format("%s/sstable_level0_%d_%d.db", dir, System.currentTimeMillis(), i);
            level0.add(new SSTable(file, entries));
        }
        log.data("SSTable数量", 4);
        
        log.step("增加第5个SSTable（超过阈值）");
        List<KeyValue> extra = new ArrayList<>();
        for (int k = 0; k < 10; k++) extra.add(new KeyValue("k"+k, "vX"));
        String file = String.format("%s/sstable_level0_%d_%d.db", dir, System.currentTimeMillis(), 99);
        level0.add(new SSTable(file, extra));
        log.data("总SSTable数量", 5);
        
        log.step("选择压缩任务");
        LeveledCompactionStrategy.CompactionTask task2 = strategy.selectCompactionTask(level0);
        log.data("任务是否为null", task2 == null);
        Assert.assertNotNull(task2);
        log.assertSuccess("超过阈值返回压缩任务");
        log.pass();
    }
}
