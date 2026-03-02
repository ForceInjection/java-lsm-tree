package com.brianxiadong.lsmtree.tools;

import com.brianxiadong.lsmtree.KeyValue;
import com.brianxiadong.lsmtree.SSTable;
import com.brianxiadong.lsmtree.TestLogger;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * SSTable分析工具测试类
 * 测试SSTable文件的分析、打印和导出功能
 */
public class SSTableAnalyzerTest {
    @Test
    public void testAnalyzeValidAndInvalidFile() throws Exception {
        TestLogger log = new TestLogger("SSTable分析工具测试");
        log.start("测试有效和无效文件的分析功能");
        
        log.step("创建临时目录和SSTable文件");
        File dir = Files.createTempDirectory("sst-analyze").toFile();
        File sstable = new File(dir, "sstable_level0_" + System.currentTimeMillis() + ".db");
        log.data("临时目录", dir.getAbsolutePath());
        log.data("SSTable文件", sstable.getName());

        log.step("创建包含5条数据的SSTable");
        List<KeyValue> entries = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            entries.add(new KeyValue("k" + i, "v" + i));
        }
        // SSTable 构造函数要求 entries 按 Key 排序
        entries.sort(KeyValue::compareTo);
        new SSTable(sstable.getAbsolutePath(), entries);
        log.data("数据条数", entries.size());

        log.step("分析有效文件");
        SSTableAnalyzer.AnalysisResult ok = SSTableAnalyzer.analyzeFile(sstable.getAbsolutePath());
        log.data("文件有效", ok.isValid());
        log.data("条目数", ok.getEntryCount());
        Assert.assertTrue(ok.isValid());
        Assert.assertEquals(5, ok.getEntryCount());
        log.assertSuccess("有效文件分析正确");
        
        log.step("打印分析结果和数据内容");
        SSTableAnalyzer.printAnalysisResult(ok);
        SSTableAnalyzer.printDataContent(ok, 2);
        
        log.step("导出到JSON文件");
        File out = new File(dir, "out.json");
        SSTableAnalyzer.exportToJson(ok, out.getAbsolutePath());
        log.data("JSON文件存在", out.exists());
        Assert.assertTrue(out.exists());
        log.assertSuccess("JSON导出成功");

        log.step("分析不存在的文件");
        SSTableAnalyzer.AnalysisResult missing = SSTableAnalyzer.analyzeFile(new File(dir, "missing.db").getAbsolutePath());
        log.data("文件有效", missing.isValid());
        log.data("错误信息", missing.getErrorMessage());
        Assert.assertFalse(missing.isValid());
        Assert.assertTrue(missing.getErrorMessage().contains("文件不存在"));
        log.assertSuccess("正确处理不存在的文件");
        log.pass();
    }
}
