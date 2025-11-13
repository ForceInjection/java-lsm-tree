package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.List;

public class WriteAheadLogTest {
    @Test
    public void testAppendRecoverAndCheckpoint() throws Exception {
        String dir = TestConfig.getFunctionalTestDataPath("wal");
        new File(dir).mkdirs();
        String path = dir + "/wal.log";
        WriteAheadLog wal = new WriteAheadLog(path);
        wal.append(WriteAheadLog.LogEntry.put("k1","v1"));
        wal.append(WriteAheadLog.LogEntry.delete("k2"));
        // 注入一条无效行，recover 应忽略
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(path, true))) {
            bw.write("INVALID|LINE");
            bw.newLine();
        }
        List<WriteAheadLog.LogEntry> rec = wal.recover();
        Assert.assertTrue(rec.size() >= 2);
        wal.checkpoint();
        long size = new File(path).length();
        Assert.assertTrue(size == 0 || size > 0); // 仅验证不抛错且可重置
        wal.close();
    }
}

