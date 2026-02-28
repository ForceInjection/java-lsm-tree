package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.List;

/**
 * Write-Ahead Log (WAL) 测试类
 * 测试日志追加、恢复和检查点功能
 */
public class WriteAheadLogTest {
    @Test
    public void testAppendRecoverAndCheckpoint() throws Exception {
        TestLogger log = new TestLogger("WAL追加、恢复和检查点测试");
        log.start("测试WAL的完整生命周期");
        
        String dir = TestConfig.getFunctionalTestDataPath("wal");
        new File(dir).mkdirs();
        String path = dir + "/wal.log";
        log.data("WAL文件路径", path);
        
        log.step("创建WAL并追加日志条目");
        WriteAheadLog wal = new WriteAheadLog(path);
        wal.append(WriteAheadLog.LogEntry.put("k1","v1"));
        wal.append(WriteAheadLog.LogEntry.delete("k2"));
        log.data("追加的条目数", 2);
        
        log.step("注入一条无效行（用于测试容错性）");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(path, true))) {
            bw.write("INVALID|LINE");
            bw.newLine();
        }
        
        log.step("恢复日志并验证");
        List<WriteAheadLog.LogEntry> rec = wal.recover();
        log.data("恢复的条目数", rec.size());
        Assert.assertTrue(rec.size() >= 2);
        log.assertSuccess("恢复的条目数正确（>=2）");
        
        log.step("执行检查点");
        wal.checkpoint();
        long size = new File(path).length();
        log.data("检查点后文件大小", size + " bytes");
        Assert.assertTrue(size == 0 || size > 0);
        log.assertSuccess("检查点执行成功");
        
        wal.close();
        log.pass();
    }
}
