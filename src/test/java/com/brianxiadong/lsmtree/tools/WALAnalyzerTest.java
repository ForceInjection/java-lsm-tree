package com.brianxiadong.lsmtree.tools;

import com.brianxiadong.lsmtree.TestLogger;
import com.brianxiadong.lsmtree.WriteAheadLog;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;

/**
 * WAL分析工具测试类
 * 测试WAL文件的分析和验证功能
 */
public class WALAnalyzerTest {
    @Test
    public void testAnalyzeWalAndValidate() throws Exception {
        TestLogger log = new TestLogger("WAL分析工具测试");
        log.start("测试WAL文件的分析和验证功能");
        
        File dir = Files.createTempDirectory("wal-analyze").toFile();
        File wal = new File(dir, "wal.log");
        log.data("临时目录", dir.getAbsolutePath());

        log.step("写入有效WAL条目");
        WriteAheadLog w = new WriteAheadLog(wal.getAbsolutePath());
        w.append(WriteAheadLog.LogEntry.put("a", "1"));
        w.append(WriteAheadLog.LogEntry.delete("b"));
        w.close();
        log.data("写入条目", "put(a,1), delete(b)");

        log.step("追加无效行（触发错误分支）");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(wal, true))) {
            bw.write("INVALID_LINE");
            bw.newLine();
        }
        log.data("追加无效行", "INVALID_LINE");

        log.step("分析WAL文件");
        WALAnalyzer.WALAnalysisResult res = WALAnalyzer.analyzeWAL(wal.getAbsolutePath());
        log.data("文件大小", res.getFileSize() + " bytes");
        log.data("总条目数", res.getStatistics().getTotalEntries());
        log.data("错误数", res.getErrors().size());
        Assert.assertTrue(res.getFileSize() > 0);
        Assert.assertTrue(res.getStatistics().getTotalEntries() >= 2);
        Assert.assertFalse(res.getErrors().isEmpty());
        log.assertSuccess("正确检测到错误");

        log.step("格式化和导出分析结果");
        String text = WALAnalyzer.formatAnalysisResult(res, true);
        log.data("报告包含标题", text.contains("WAL文件分析报告"));
        Assert.assertTrue(text.contains("WAL文件分析报告"));
        
        File out = new File(dir, "wal.json");
        WALAnalyzer.exportToJSON(res, out.getAbsolutePath());
        log.data("JSON文件存在", out.exists());
        Assert.assertTrue(out.exists());
        log.assertSuccess("导出成功");

        log.step("验证包含无效行的WAL");
        boolean valid1 = WALAnalyzer.validateWAL(wal.getAbsolutePath());
        log.data("验证结果", valid1);
        Assert.assertFalse(valid1);
        log.assertSuccess("正确识别为无效");

        log.step("验证纯有效日志");
        File wal2 = new File(dir, "wal2.log");
        WriteAheadLog w2 = new WriteAheadLog(wal2.getAbsolutePath());
        w2.append(WriteAheadLog.LogEntry.put("c", "3"));
        w2.append(WriteAheadLog.LogEntry.delete("d"));
        w2.close();
        boolean valid2 = WALAnalyzer.validateWAL(wal2.getAbsolutePath());
        log.data("验证结果", valid2);
        Assert.assertTrue(valid2);
        log.assertSuccess("正确识别为有效");
        log.pass();
    }
}

