package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.Iterator;

/**
 * 范围查询版本选择测试类
 * 测试跨层版本选择功能
 */
public class RangeQueryVersionSelectionAcrossLayersTest {
    @Test
    public void testLatestAcrossSSTableAndMemtable() throws Exception {
        TestLogger log = new TestLogger("跨层版本选择测试");
        log.start("测试跨SSTable和MemTable的版本选择");
        
        log.step("创建LSMTree（阈值=2）");
        LSMTree tree = new LSMTree(TestConfig.getFunctionalTestDataPath("range-version"), 2);
        log.data("MemTable阈值", 2);
        
        log.step("写入k=v1并刷盘到SSTable");
        tree.put("k","v1");
        tree.put("x","dummy"); // 触发 flush
        tree.flush();
        log.data("写入", "k=v1, x=dummy");
        log.data("刷盘后k的位置", "SSTable");
        
        log.step("写入k=v2到MemTable");
        tree.put("k","v2");
        log.data("更新", "k=v2");
        log.data("更新后k的位置", "MemTable");

        log.step("执行范围查询[k,k]");
        Iterator<KeyValue> it = tree.range("k","k", true, true);
        Assert.assertTrue(it.hasNext());
        KeyValue kv = it.next();
        log.data("返回key", kv.getKey());
        log.data("返回value", kv.getValue());
        Assert.assertEquals("k", kv.getKey());
        Assert.assertEquals("v2", kv.getValue());
        Assert.assertFalse(it.hasNext());
        log.assertSuccess("返回最新版本v2（来自MemTable）");
        tree.close();
        log.pass();
    }
}

