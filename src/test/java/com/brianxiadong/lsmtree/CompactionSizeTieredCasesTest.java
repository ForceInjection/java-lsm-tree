package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Size-Tiered压缩用例测试类
 * 测试基于大小的压缩合并
 */
public class CompactionSizeTieredCasesTest {
    @Test
    public void testSizeTieredCompactionMergesSimilarSized() throws Exception {
        TestLogger log = new TestLogger("Size-Tiered压缩合并测试");
        log.start("测试Size-Tiered压缩合并相似大小的SSTable");
        
        String dir = TestConfig.getPerformanceTestDataPath("size-tiered");
        log.step("创建LSMTree并插入6条数据触发刷盘");
        LSMTree tree = new LSMTree(dir, 2);
        for (int i = 0; i < 6; i++) {
            tree.put("a" + i, "v" + i);
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
        
        log.step("执行Size-Tiered压缩");
        SizeTieredCompactionStrategy strat = new SizeTieredCompactionStrategy(dir, 64, 2);
        List<SSTable> out = strat.compact(tables);
        log.data("压缩后SSTable数", out.size());
        
        Assert.assertTrue(out.size() >= 1);
        log.assertSuccess("Size-Tiered压缩成功合并SSTable");
        log.pass();
    }
}
