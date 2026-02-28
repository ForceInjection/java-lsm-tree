package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 分层压缩策略测试类
 * 测试Leveled Compaction的触发和执行
 */
public class LeveledCompactionStrategyTest {
    @Test
    public void testNeedsAndCompact() throws Exception {
        TestLogger log = new TestLogger("分层压缩触发和执行测试");
        log.start("测试压缩触发条件和执行结果");
        
        String dir = Files.createTempDirectory("lsm-compact").toFile().getAbsolutePath();
        log.data("临时目录", dir);
        
        log.step("创建LeveledCompactionStrategy（level0Size=4, maxLevels=10）");
        LeveledCompactionStrategy strategy = new LeveledCompactionStrategy(dir, 4, 10);

        log.step("创建6个Level0 SSTable（超过阈值4）");
        List<SSTable> level0 = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            List<KeyValue> entries = new ArrayList<>();
            for (int k = 0; k < 100; k++) {
                entries.add(new KeyValue("k" + k, "v" + i));
            }
            String file = String.format("%s/sstable_level0_%d_%d.db", dir, System.currentTimeMillis(), i);
            level0.add(new SSTable(file, entries));
        }
        log.data("SSTable数量", level0.size());

        log.step("检查压缩触发条件");
        boolean needsCompact = strategy.needsCompaction(level0);
        log.data("需要压缩", needsCompact);
        Assert.assertTrue(needsCompact);
        log.assertSuccess("正确检测到需要压缩");
        
        log.step("执行压缩");
        List<SSTable> after = strategy.compact(level0);
        log.data("压缩后SSTable数量", after.size());
        Assert.assertFalse(after.isEmpty());
        
        boolean allNextLevel = after.stream().allMatch(t -> t.getFilePath().contains("level1"));
        log.data("所有SSTable都升级到Level1", allNextLevel);
        Assert.assertTrue(allNextLevel);
        log.assertSuccess("压缩正确地将数据升级到下一层");
        log.pass();
    }

    @Test
    public void testCompactOnEmpty() throws IOException {
        TestLogger log = new TestLogger("空列表压缩测试");
        log.start("测试空SSTable列表的压缩处理");
        
        String dir = Files.createTempDirectory("lsm-compact-empty").toFile().getAbsolutePath();
        LeveledCompactionStrategy strategy = new LeveledCompactionStrategy(dir, 4, 10);
        
        log.step("对空列表执行压缩");
        List<SSTable> res = strategy.compact(new ArrayList<>());
        log.data("返回结果大小", res.size());
        Assert.assertTrue(res.isEmpty());
        log.assertSuccess("空列表压缩返回空结果");
        log.pass();
    }
}
