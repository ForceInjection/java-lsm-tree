package com.brianxiadong.lsmtree;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.File;
import java.io.IOException;
import static org.junit.Assert.*;

/**
 * LSM Tree 简化测试类
 */
public class SimpleLSMTreeTest {
    private LSMTree lsmTree;
    private String testDir;

    @Before
    public void setUp() throws IOException {
        testDir = "simple_test_" + System.currentTimeMillis();
        lsmTree = new LSMTree(testDir, 10); // 小容量，容易触发刷盘
    }

    @After
    public void tearDown() throws IOException {
        if (lsmTree != null) {
            lsmTree.close();
        }

        // 清理测试数据
        deleteDirectory(new File(testDir));
    }

    @Test
    public void testBasicOperations() throws IOException {
        // 基本操作测试
        lsmTree.put("key1", "value1");
        assertEquals("value1", lsmTree.get("key1"));

        lsmTree.put("key1", "updated");
        assertEquals("updated", lsmTree.get("key1"));

        lsmTree.delete("key1");
        assertNull(lsmTree.get("key1"));
    }

    @Test
    public void testSmallFlush() throws IOException {
        // 插入少量数据并刷盘
        for (int i = 0; i < 5; i++) {
            lsmTree.put("key" + i, "value" + i);
        }

        // 手动刷盘
        lsmTree.flush();

        // 验证数据
        for (int i = 0; i < 5; i++) {
            assertEquals("value" + i, lsmTree.get("key" + i));
        }
    }

    @Test
    public void testAutoFlush() throws IOException {
        // 插入足够数据触发自动刷盘
        for (int i = 0; i < 15; i++) { // 超过容量10
            lsmTree.put("key" + i, "value" + i);
        }

        // 验证数据
        for (int i = 0; i < 15; i++) {
            assertEquals("value" + i, lsmTree.get("key" + i));
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