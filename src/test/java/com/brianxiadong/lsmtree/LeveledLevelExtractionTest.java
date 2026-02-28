package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 分层压缩级别提取测试类
 * 测试从文件名提取级别的边界情况
 */
public class LeveledLevelExtractionTest {
    @Test
    public void testUnknownFilenameDefaultsToLevel0() throws Exception {
        TestLogger log = new TestLogger("未知文件名级别提取测试");
        log.start("测试未知格式文件名默认为Level0");
        
        String dir = Files.createTempDirectory("lsm-level-extract").toFile().getAbsolutePath();
        String file = dir + "/abcd_" + System.currentTimeMillis() + ".db";
        log.data("临时目录", dir);
        log.data("文件名", "abcd_xxx.db（未知格式）");
        
        log.step("创建SSTable");
        List<KeyValue> data = new ArrayList<>();
        data.add(new KeyValue("a","v"));
        SSTable t = new SSTable(file, data);
        log.data("数据条数", 1);
        
        log.step("选择压缩任务");
        List<SSTable> list = new ArrayList<>();
        list.add(t);
        LeveledCompactionStrategy s = new LeveledCompactionStrategy(dir, 4, 10);
        LeveledCompactionStrategy.CompactionTask task = s.selectCompactionTask(list);
        log.data("任务是否为null", task == null);
        Assert.assertNotNull(task);
        log.assertSuccess("未知格式文件名默认为Level0");
        log.pass();
    }
}
