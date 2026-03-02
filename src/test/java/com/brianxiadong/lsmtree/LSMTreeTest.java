package com.brianxiadong.lsmtree;

import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

/**
 * LSM Tree 核心功能测试类
 * 测试LSM Tree的基本CRUD操作、刷盘、恢复等核心功能
 * 
 * 使用 LSMTreeTestBase 提供的统一生命周期管理和工具方法
 */
public class LSMTreeTest extends LSMTreeTestBase {
    
    @Test
    public void testBasicPutAndGet() throws IOException {
        logger = new TestLogger("基本读写测试");
        logger.start("测试put和get基本操作");
        
        logger.step("写入key1=value1, key2=value2");
        lsmTree.put("key1", "value1");
        lsmTree.put("key2", "value2");
        
        logger.step("读取并验证数据");
        String v1 = lsmTree.get("key1");
        String v2 = lsmTree.get("key2");
        logger.data("key1值", v1);
        logger.data("key2值", v2);
        assertEquals("value1", v1);
        assertEquals("value2", v2);
        logger.assertSuccess("读取的值与写入的一致");
        
        String missing = lsmTree.get("nonexistent");
        logger.data("不存在的key返回", missing);
        assertNull(missing);
        logger.assertSuccess("不存在的key返回null");
        logger.pass();
    }

    @Test
    public void testUpdate() throws IOException {
        logger = new TestLogger("更新测试");
        logger.start("测试更新已存在的key");
        
        logger.step("写入key1=value1");
        lsmTree.put("key1", "value1");
        logger.data("初始值", lsmTree.get("key1"));
        assertEquals("value1", lsmTree.get("key1"));
        
        logger.step("更新key1=updated_value");
        lsmTree.put("key1", "updated_value");
        logger.data("更新后的值", lsmTree.get("key1"));
        assertEquals("updated_value", lsmTree.get("key1"));
        logger.assertSuccess("更新操作正确覆盖了原值");
        logger.pass();
    }

    @Test
    public void testDelete() throws IOException {
        logger = new TestLogger("删除测试");
        logger.start("测试删除key操作");
        
        logger.step("写入并读取key1=value1");
        lsmTree.put("key1", "value1");
        logger.data("删除前的值", lsmTree.get("key1"));
        assertEquals("value1", lsmTree.get("key1"));
        
        logger.step("删除key1");
        lsmTree.delete("key1");
        logger.data("删除后的值", lsmTree.get("key1"));
        assertNull(lsmTree.get("key1"));
        logger.assertSuccess("删除后key返回null");
        logger.pass();
    }

    @Test
    public void testLargeDataSet() throws IOException {
        logger = new TestLogger("大数据集测试");
        logger.start("测试插入1000条数据并验证");
        
        logger.step("插入1000条数据");
        bulkInsert(0, 1000);
        logger.data("写入条数", 1000);
        
        LSMTree.LSMTreeStats stats = lsmTree.getStats();
        logger.data("MemTable大小", stats.getActiveMemTableSize());
        logger.data("SSTable数量", stats.getSsTableCount());
        
        logger.step("验证所有数据");
        verifyDataRange(0, 1000);
        logger.data("验证通过条数", 1000);
        logger.assertSuccess("所有1000条数据验证通过");
        logger.pass();
    }

    @Test
    public void testMemTableFlush() throws IOException {
        logger = new TestLogger("MemTable刷盘测试");
        logger.start("测试MemTable刷盘到SSTable");
        
        logger.step("插入150条数据（超过MemTable容量100）");
        bulkInsert(0, 150);
        
        LSMTree.LSMTreeStats statsBefore = lsmTree.getStats();
        logger.data("刷盘前MemTable大小", statsBefore.getActiveMemTableSize());
        
        logger.step("强制刷盘");
        lsmTree.flush();
        
        LSMTree.LSMTreeStats statsAfter = lsmTree.getStats();
        logger.data("刷盘后MemTable大小", statsAfter.getActiveMemTableSize());
        logger.data("刷盘后SSTable数量", statsAfter.getSsTableCount());
        
        logger.step("验证数据仍然可读取");
        verifyDataRange(0, 150);
        logger.data("验证通过条数", 150);
        logger.assertSuccess("刷盘后数据完整");
        logger.pass();
    }

    @Test
    public void testRecovery() throws IOException {
        logger = new TestLogger("数据恢复测试");
        logger.start("测试关闭后重新打开LSM Tree数据恢复");
        
        logger.step("写入持久化数据");
        lsmTree.put("persistent_key1", "persistent_value1");
        lsmTree.put("persistent_key2", "persistent_value2");
        logger.data("写入条数", 2);
        
        logger.step("刷盘并关闭LSM Tree");
        lsmTree.flush();
        lsmTree.close();
        
        logger.step("重新打开LSM Tree");
        lsmTree = createLSMTree();
        
        logger.step("验证数据恢复");
        String v1 = lsmTree.get("persistent_key1");
        String v2 = lsmTree.get("persistent_key2");
        logger.data("persistent_key1", v1);
        logger.data("persistent_key2", v2);
        assertEquals("persistent_value1", v1);
        assertEquals("persistent_value2", v2);
        logger.assertSuccess("数据恢复成功");
        logger.pass();
    }

    @Test
    public void testStats() throws IOException {
        logger = new TestLogger("统计信息测试");
        logger.start("测试LSM Tree统计信息");
        
        LSMTree.LSMTreeStats stats = lsmTree.getStats();
        logger.data("初始MemTable大小", stats.getActiveMemTableSize());
        logger.data("初始Immutable MemTable数量", stats.getImmutableMemTableCount());
        assertEquals(0, stats.getActiveMemTableSize());
        assertEquals(0, stats.getImmutableMemTableCount());
        logger.assertSuccess("初始状态正确");
        
        logger.step("写入一条数据");
        lsmTree.put("key1", "value1");
        stats = lsmTree.getStats();
        logger.data("写入后MemTable大小", stats.getActiveMemTableSize());
        assertEquals(1, stats.getActiveMemTableSize());
        logger.assertSuccess("统计信息更新正确");
        logger.pass();
    }
}
