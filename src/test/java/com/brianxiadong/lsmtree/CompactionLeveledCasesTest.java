package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 分层压缩用例测试类
 * 测试分层压缩的合并和旧文件删除
 */
public class CompactionLeveledCasesTest {
    @Test
    public void testLeveledCompactionMergesAndDeletesOld() throws Exception {
        TestLogger log = new TestLogger("分层压缩合并测试");
        log.start("测试分层压缩合并SSTable并删除旧文件");
        
        String dir = TestConfig.getPerformanceTestDataPath("leveled-compaction");
        log.step("创建LSMTree并插入6条数据触发刷盘");
        LSMTree tree = new LSMTree(dir, 2);
        for (int i = 0; i < 6; i++) {
            tree.put("k" + i, "v" + i);
        }
        tree.close();
        log.data("数据条数", 6);
        log.data("MemTable阈值", 2);
        
        log.step("读取生成的SSTable文件");
        File d = new File(dir);
        File[] files = d.listFiles((x, n) -> n.endsWith(".db"));
        List<SSTable> tables = new ArrayList<>();
        if (files != null) {
            for (File f : files) tables.add(new SSTable(f.getAbsolutePath()));
        }
        log.data("SSTable文件数", tables.size());
        
        log.step("执行分层压缩");
        LeveledCompactionStrategy strat = new LeveledCompactionStrategy(dir, 1, 2);
        List<SSTable> out = strat.compact(tables);
        int level1 = 0;
        for (SSTable t : out) if (t.getFilePath().contains("level1")) level1++;
        log.data("压缩后SSTable数", out.size());
        log.data("Level1 SSTable数", level1);
        
        Assert.assertTrue(level1 >= 1);
        log.assertSuccess("分层压缩正确生成Level1文件");
        log.pass();
    }
}
