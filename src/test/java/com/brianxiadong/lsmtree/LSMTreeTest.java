package com.brianxiadong.lsmtree;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.File;
import java.io.IOException;
import static org.junit.Assert.*;

/**
 * LSM Tree 测试类
 */
public class LSMTreeTest {
    private LSMTree lsmTree;
    private String testDir;

    @Before
    public void setUp() throws IOException {
        testDir = "test_data_" + System.currentTimeMillis();
        lsmTree = new LSMTree(testDir, 100);
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
    public void testBasicPutAndGet() throws IOException {
        lsmTree.put("key1", "value1");
        lsmTree.put("key2", "value2");

        assertEquals("value1", lsmTree.get("key1"));
        assertEquals("value2", lsmTree.get("key2"));
        assertNull(lsmTree.get("nonexistent"));
    }

    @Test
    public void testUpdate() throws IOException {
        lsmTree.put("key1", "value1");
        assertEquals("value1", lsmTree.get("key1"));

        lsmTree.put("key1", "updated_value");
        assertEquals("updated_value", lsmTree.get("key1"));
    }

    @Test
    public void testDelete() throws IOException {
        lsmTree.put("key1", "value1");
        assertEquals("value1", lsmTree.get("key1"));

        lsmTree.delete("key1");
        assertNull(lsmTree.get("key1"));
    }

    @Test
    public void testLargeDataSet() throws IOException {
        // 插入大量数据触发刷盘
        for (int i = 0; i < 1000; i++) {
            lsmTree.put("key" + i, "value" + i);
        }

        // 验证数据
        for (int i = 0; i < 1000; i++) {
            assertEquals("value" + i, lsmTree.get("key" + i));
        }
    }

    @Test
    public void testMemTableFlush() throws IOException {
        // 插入超过MemTable容量的数据
        for (int i = 0; i < 150; i++) {
            lsmTree.put("key" + i, "value" + i);
        }

        // 强制刷盘
        lsmTree.flush();

        // 验证数据仍然可以读取
        for (int i = 0; i < 150; i++) {
            assertEquals("value" + i, lsmTree.get("key" + i));
        }
    }

    @Test
    public void testRecovery() throws IOException {
        // 插入一些数据
        lsmTree.put("persistent_key1", "persistent_value1");
        lsmTree.put("persistent_key2", "persistent_value2");
        lsmTree.flush();

        // 关闭LSM Tree
        lsmTree.close();

        // 重新打开
        lsmTree = new LSMTree(testDir, 100);

        // 验证数据恢复
        assertEquals("persistent_value1", lsmTree.get("persistent_key1"));
        assertEquals("persistent_value2", lsmTree.get("persistent_key2"));
    }

    @Test
    public void testStats() throws IOException {
        LSMTree.LSMTreeStats stats = lsmTree.getStats();
        assertEquals(0, stats.getActiveMemTableSize());
        assertEquals(0, stats.getImmutableMemTableCount());

        lsmTree.put("key1", "value1");
        stats = lsmTree.getStats();
        assertEquals(1, stats.getActiveMemTableSize());
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