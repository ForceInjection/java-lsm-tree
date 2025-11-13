package com.brianxiadong.lsmtree.tools;

import com.brianxiadong.lsmtree.WriteAheadLog;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;

public class WALAnalyzerTest {
    @Test
    public void testAnalyzeWalAndValidate() throws Exception {
        File dir = Files.createTempDirectory("wal-analyze").toFile();
        File wal = new File(dir, "wal.log");

        // 写入有效 WAL
        WriteAheadLog w = new WriteAheadLog(wal.getAbsolutePath());
        w.append(WriteAheadLog.LogEntry.put("a", "1"));
        w.append(WriteAheadLog.LogEntry.delete("b"));
        w.close();

        // 追加一条无效行，触发错误分支
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(wal, true))) {
            bw.write("INVALID_LINE");
            bw.newLine();
        }

        WALAnalyzer.WALAnalysisResult res = WALAnalyzer.analyzeWAL(wal.getAbsolutePath());
        Assert.assertTrue(res.getFileSize() > 0);
        Assert.assertTrue(res.getStatistics().getTotalEntries() >= 2);
        Assert.assertFalse(res.getErrors().isEmpty());

        // 覆盖格式化与导出
        String text = WALAnalyzer.formatAnalysisResult(res, true);
        Assert.assertTrue(text.contains("WAL文件分析报告"));
        File out = new File(dir, "wal.json");
        WALAnalyzer.exportToJSON(res, out.getAbsolutePath());
        Assert.assertTrue(out.exists());

        // 校验接口：包含无效行 -> false
        Assert.assertFalse(WALAnalyzer.validateWAL(wal.getAbsolutePath()));

        // 纯有效日志 -> true
        File wal2 = new File(dir, "wal2.log");
        WriteAheadLog w2 = new WriteAheadLog(wal2.getAbsolutePath());
        w2.append(WriteAheadLog.LogEntry.put("c", "3"));
        w2.append(WriteAheadLog.LogEntry.delete("d"));
        w2.close();
        Assert.assertTrue(WALAnalyzer.validateWAL(wal2.getAbsolutePath()));
    }
}

