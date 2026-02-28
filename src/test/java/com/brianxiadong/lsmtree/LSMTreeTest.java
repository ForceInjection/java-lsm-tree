package com.brianxiadong.lsmtree;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.File;
import java.io.IOException;
import static org.junit.Assert.*;

/**
 * LSM Tree 核心功能测试类
 * 测试LSM Tree的基本CRUD操作、刷盘、恢复等核心功能
 */
public class LSMTreeTest {
    private LSMTree lsmTree;
    private String testDir;
    private TestLogger log;

    @Before
    public void setUp() throws IOException {
        testDir = "test_data_" + System.currentTimeMillis();
        lsmTree = new LSMTree(testDir, 100);
        log = new TestLogger("");
        System.out.println("\n初始化测试环境: 目录=" + testDir + ", MemTable大小=100");
    }

    @After
    public void tearDown() throws IOException {
        if (lsmTree != null) {
            lsmTree.close();
        }
        deleteDirectory(new File(testDir));
        System.out.println("清理测试环境完成");
    }

    @Test
    public void testBasicPutAndGet() throws IOException {
        log = new TestLogger("基本读写测试");
        log.start("测试put和get基本操作");
        
        log.step("写入key1=value1, key2=value2");
        lsmTree.put("key1", "value1");
        lsmTree.put("key2", "value2");
        
        log.step("读取并验证数据");
        String v1 = lsmTree.get("key1");
        String v2 = lsmTree.get("key2");
        log.data("key1值", v1);
        log.data("key2值", v2);
        assertEquals("value1", v1);
        assertEquals("value2", v2);
        log.assertSuccess("读取的值与写入的一致");
        
        String missing = lsmTree.get("nonexistent");
        log.data("不存在的key返回", missing);
        assertNull(missing);
        log.assertSuccess("不存在的key返回null");
        log.pass();
    }

    @Test
    public void testUpdate() throws IOException {
        log = new TestLogger("更新测试");
        log.start("测试更新已存在的key");
        
        log.step("写入key1=value1");
        lsmTree.put("key1", "value1");
        log.data("初始值", lsmTree.get("key1"));
        assertEquals("value1", lsmTree.get("key1"));
        
        log.step("更新key1=updated_value");
        lsmTree.put("key1", "updated_value");
        log.data("更新后的值", lsmTree.get("key1"));
        assertEquals("updated_value", lsmTree.get("key1"));
        log.assertSuccess("更新操作正确覆盖了原值");
        log.pass();
    }

    @Test
    public void testDelete() throws IOException {
        log = new TestLogger("删除测试");
        log.start("测试删除key操作");
        
        log.step("写入并读取key1=value1");
        lsmTree.put("key1", "value1");
        log.data("删除前的值", lsmTree.get("key1"));
        assertEquals("value1", lsmTree.get("key1"));
        
        log.step("删除key1");
        lsmTree.delete("key1");
        log.data("删除后的值", lsmTree.get("key1"));
        assertNull(lsmTree.get("key1"));
        log.assertSuccess("删除后key返回null");
        log.pass();
    }

    @Test
    public void testLargeDataSet() throws IOException {
        log = new TestLogger("大数据集测试");
        log.start("测试插入1000条数据并验证");
        
        log.step("插入1000条数据");
        for (int i = 0; i < 1000; i++) {
            lsmTree.put("key" + i, "value" + i);
        }
        log.data("写入条数", 1000);
        
        LSMTree.LSMTreeStats stats = lsmTree.getStats();
        log.data("MemTable大小", stats.getActiveMemTableSize());
        log.data("SSTable数量", stats.getSsTableCount());
        
        log.step("验证所有数据");
        int verified = 0;
        for (int i = 0; i < 1000; i++) {
            assertEquals("value" + i, lsmTree.get("key" + i));
            verified++;
        }
        log.data("验证通过条数", verified);
        log.assertSuccess("所有1000条数据验证通过");
        log.pass();
    }

    @Test
    public void testMemTableFlush() throws IOException {
        log = new TestLogger("MemTable刷盘测试");
        log.start("测试MemTable刷盘到SSTable");
        
        log.step("插入150条数据（超过MemTable容量100）");
        for (int i = 0; i < 150; i++) {
            lsmTree.put("key" + i, "value" + i);
        }
        
        LSMTree.LSMTreeStats statsBefore = lsmTree.getStats();
        log.data("刷盘前MemTable大小", statsBefore.getActiveMemTableSize());
        
        log.step("强制刷盘");
        lsmTree.flush();
        
        LSMTree.LSMTreeStats statsAfter = lsmTree.getStats();
        log.data("刷盘后MemTable大小", statsAfter.getActiveMemTableSize());
        log.data("刷盘后SSTable数量", statsAfter.getSsTableCount());
        
        log.step("验证数据仍然可读取");
        int verified = 0;
        for (int i = 0; i < 150; i++) {
            assertEquals("value" + i, lsmTree.get("key" + i));
            verified++;
        }
        log.data("验证通过条数", verified);
        log.assertSuccess("刷盘后数据完整");
        log.pass();
    }

    @Test
    public void testRecovery() throws IOException {
        log = new TestLogger("数据恢复测试");
        log.start("测试关闭后重新打开LSM Tree数据恢复");
        
        log.step("写入持久化数据");
        lsmTree.put("persistent_key1", "persistent_value1");
        lsmTree.put("persistent_key2", "persistent_value2");
        log.data("写入条数", 2);
        
        log.step("刷盘并关闭LSM Tree");
        lsmTree.flush();
        lsmTree.close();
        
        log.step("重新打开LSM Tree");
        lsmTree = new LSMTree(testDir, 100);
        
        log.step("验证数据恢复");
        String v1 = lsmTree.get("persistent_key1");
        String v2 = lsmTree.get("persistent_key2");
        log.data("persistent_key1", v1);
        log.data("persistent_key2", v2);
        assertEquals("persistent_value1", v1);
        assertEquals("persistent_value2", v2);
        log.assertSuccess("数据恢复成功");
        log.pass();
    }

    @Test
    public void testStats() throws IOException {
        log = new TestLogger("统计信息测试");
        log.start("测试LSM Tree统计信息");
        
        LSMTree.LSMTreeStats stats = lsmTree.getStats();
        log.data("初始MemTable大小", stats.getActiveMemTableSize());
        log.data("初始Immutable MemTable数量", stats.getImmutableMemTableCount());
        assertEquals(0, stats.getActiveMemTableSize());
        assertEquals(0, stats.getImmutableMemTableCount());
        log.assertSuccess("初始状态正确");
        
        log.step("写入一条数据");
        lsmTree.put("key1", "value1");
        stats = lsmTree.getStats();
        log.data("写入后MemTable大小", stats.getActiveMemTableSize());
        assertEquals(1, stats.getActiveMemTableSize());
        log.assertSuccess("统计信息更新正确");
        log.pass();
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