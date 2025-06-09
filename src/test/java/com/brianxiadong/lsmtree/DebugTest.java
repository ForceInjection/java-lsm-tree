package com.brianxiadong.lsmtree;

import org.junit.Test;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

/**
 * 调试测试
 */
public class DebugTest {

    @Test
    public void testSSTableWriteAndRead() throws IOException {
        String testFile = "debug_sstable.db";

        try {
            // 创建测试数据
            List<KeyValue> data = Arrays.asList(
                    new KeyValue("key1", "value1"),
                    new KeyValue("key2", "value2"));

            // 写入SSTable
            SSTable writeTable = new SSTable(testFile, data);

            // 直接从写入的SSTable读取
            assertEquals("value1", writeTable.get("key1"));
            assertEquals("value2", writeTable.get("key2"));

            // 从文件加载新的SSTable
            SSTable readTable = new SSTable(testFile);

            // 测试读取
            assertEquals("value1", readTable.get("key1"));
            assertEquals("value2", readTable.get("key2"));

        } finally {
            // 清理文件
            new File(testFile).delete();
        }
    }

    @Test
    public void testLSMTreeRecoverySimple() throws IOException {
        String testDir = "debug_lsm_" + System.currentTimeMillis();

        try {
            // 第一阶段：写入数据并刷盘
            LSMTree lsm1 = new LSMTree(testDir, 10);
            lsm1.put("key1", "value1");
            lsm1.flush();
            lsm1.close();

            // 检查文件是否创建
            File dir = new File(testDir);
            File[] files = dir.listFiles((d, name) -> name.endsWith(".db"));
            System.out.println("SSTable files count: " + (files != null ? files.length : 0));
            if (files != null) {
                for (File f : files) {
                    System.out.println("File: " + f.getName() + ", size: " + f.length());
                }
            }

            // 第二阶段：重新打开并读取
            LSMTree lsm2 = new LSMTree(testDir, 10);
            String result = lsm2.get("key1");
            System.out.println("Recovery result: " + result);
            assertEquals("value1", result);
            lsm2.close();

        } finally {
            // 清理
            deleteDirectory(new File(testDir));
        }
    }

    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
}