package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 分层压缩NoOp测试类
 * 测试压缩条件不满足时返回null
 */
public class LeveledCompactionNoOpTest {
    @Test
    public void testSelectTaskReturnsNullWhenBelowThreshold() throws Exception {
        TestLogger log = new TestLogger("分层压缩NoOp测试");
        log.start("测试SSTable数量低于阈值时不执行压缩");
        
        String dir = Files.createTempDirectory("lsm-level-noop").toFile().getAbsolutePath();
        log.step("创建LeveledCompactionStrategy（阈值=10）");
        LeveledCompactionStrategy s = new LeveledCompactionStrategy(dir, 10, 10);
        log.data("压缩阈值", 10);
        
        log.step("创建3个SSTable（低于阈值）");
        List<SSTable> list = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            List<KeyValue> data = new ArrayList<>();
            data.add(new KeyValue("a"+i, "v"+i));
            String file = String.format("%s/sstable_level0_%d_%d.db", dir, System.currentTimeMillis(), i);
            list.add(new SSTable(file, data));
        }
        log.data("SSTable数量", list.size());
        
        log.step("尝试选择压缩任务");
        LeveledCompactionStrategy.CompactionTask t = s.selectCompactionTask(list);
        log.data("返回的任务", t == null ? "null（无压缩）" : "非null");
        
        Assert.assertNull(t);
        log.assertSuccess("SSTable数量不足，不执行压缩");
        log.pass();
    }
}

