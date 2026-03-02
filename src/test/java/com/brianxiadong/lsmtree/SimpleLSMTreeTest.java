package com.brianxiadong.lsmtree;

import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

/**
 * LSM Tree 简化测试类
 * 测试LSM Tree的基本操作和刷盘功能
 * 
 * 使用 LSMTreeTestBase 提供的统一生命周期管理和工具方法
 */
public class SimpleLSMTreeTest extends LSMTreeTestBase {
    
    /**
     * 覆盖默认 MemTable 大小，使用较小的值以便快速触发刷盘
     */
    @Override
    protected int getDefaultMemTableSize() {
        return 10;
    }

    @Test
    public void testBasicOperations() throws IOException {
        TestLogger log = new TestLogger("基本操作测试");
        log.start("测试put/get/delete基本操作");
        
        log.step("测试写入操作");
        lsmTree.put("key1", "value1");
        log.data("写入", "key1=value1");
        assertEquals("value1", lsmTree.get("key1"));
        log.assertSuccess("读取成功");

        log.step("测试更新操作");
        lsmTree.put("key1", "updated");
        log.data("更新", "key1=updated");
        assertEquals("updated", lsmTree.get("key1"));
        log.assertSuccess("更新成功");

        log.step("测试删除操作");
        lsmTree.delete("key1");
        assertNull(lsmTree.get("key1"));
        log.assertSuccess("删除成功");
        log.pass();
    }

    @Test
    public void testSmallFlush() throws IOException {
        TestLogger log = new TestLogger("手动刷盘测试");
        log.start("测试手动刷盘后数据持久化");
        
        log.step("插入5条数据");
        bulkInsert(0, 5);
        log.data("数据条数", 5);

        log.step("执行手动刷盘");
        lsmTree.flush();
        log.assertSuccess("刷盘完成");

        log.step("验证数据");
        int found = 0;
        for (int i = 0; i < 5; i++) {
            String val = lsmTree.get("key" + i);
            if (("value" + i).equals(val)) found++;
        }
        log.data("成功读取条数", found);
        verifyDataRange(0, 5);
        log.assertSuccess("刷盘后数据完整");
        log.pass();
    }

    @Test
    public void testAutoFlush() throws IOException {
        TestLogger log = new TestLogger("自动刷盘测试");
        log.start("测试超过阈值时自动刷盘");
        
        log.step("插入15条数据（超过阈值10）");
        bulkInsert(0, 15);
        log.data("数据条数", 15);
        log.data("MemTable阈值", 10);

        log.step("验证数据完整性");
        int found = 0;
        for (int i = 0; i < 15; i++) {
            String val = lsmTree.get("key" + i);
            if (("value" + i).equals(val)) found++;
        }
        log.data("成功读取条数", found);
        verifyDataRange(0, 15);
        log.assertSuccess("自动刷盘后数据完整");
        log.pass();
    }
}
