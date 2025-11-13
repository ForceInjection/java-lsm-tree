package com.brianxiadong.lsmtree.tools;

import com.brianxiadong.lsmtree.KeyValue;
import com.brianxiadong.lsmtree.SSTable;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class SSTableAnalyzerTest {
    @Test
    public void testAnalyzeValidAndInvalidFile() throws Exception {
        // 创建临时目录与未压缩的 SSTable 文件
        File dir = Files.createTempDirectory("sst-analyze").toFile();
        File sstable = new File(dir, "sstable_level0_" + System.currentTimeMillis() + ".db");

        List<KeyValue> entries = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            entries.add(new KeyValue("k" + i, "v" + i));
        }
        entries.sort(KeyValue::compareTo);
        new SSTable(sstable.getAbsolutePath(), entries);

        // 有效文件分析
        SSTableAnalyzer.AnalysisResult ok = SSTableAnalyzer.analyzeFile(sstable.getAbsolutePath());
        Assert.assertTrue(ok.isValid());
        Assert.assertEquals(5, ok.getEntryCount());
        // 打印与导出覆盖分支
        SSTableAnalyzer.printAnalysisResult(ok);
        SSTableAnalyzer.printDataContent(ok, 2);
        File out = new File(dir, "out.json");
        SSTableAnalyzer.exportToJson(ok, out.getAbsolutePath());
        Assert.assertTrue(out.exists());

        // 不存在文件
        SSTableAnalyzer.AnalysisResult missing = SSTableAnalyzer.analyzeFile(new File(dir, "missing.db").getAbsolutePath());
        Assert.assertFalse(missing.isValid());
        Assert.assertTrue(missing.getErrorMessage().contains("文件不存在"));
    }
}

