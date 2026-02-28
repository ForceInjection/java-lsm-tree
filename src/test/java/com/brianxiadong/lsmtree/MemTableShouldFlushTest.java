package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

/**
 * MemTable刷盘测试类
 * 测试MemTable的刷盘触发条件
 */
public class MemTableShouldFlushTest {
    @Test
    public void testShouldFlushAtThreshold() {
        TestLogger log = new TestLogger("MemTable刷盘阈值测试");
        log.start("测试MemTable达到阈值时触发刷盘");
        
        log.step("创建MemTable（阈值=3）");
        MemTable mt = new MemTable(3);
        log.data("刷盘阈值", 3);
        
        log.step("插入2条数据（未达阈值）");
        mt.put("k1","v1");
        mt.put("k2","v2");
        log.data("当前条目数", 2);
        log.data("shouldFlush", mt.shouldFlush());
        Assert.assertFalse(mt.shouldFlush());
        log.assertSuccess("未达阈值，不触发刷盘");
        
        log.step("插入第3条数据（达到阈值）");
        mt.put("k3","v3");
        log.data("当前条目数", 3);
        log.data("shouldFlush", mt.shouldFlush());
        Assert.assertTrue(mt.shouldFlush());
        log.assertSuccess("达到阈值，触发刷盘");
        log.pass();
    }
}
