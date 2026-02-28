package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * MemTable范围查询测试类
 * 测试MemTable的范围查询功能
 */
public class MemTableRangeTest {
    @Test
    public void testIncludeFlagsAndDeletes() {
        TestLogger log = new TestLogger("MemTable范围查询测试");
        log.start("测试MemTable范围查询的包含标志和删除处理");
        
        log.step("创建MemTable并插入数据");
        MemTable mt = new MemTable(100);
        mt.put("a1","v1");
        mt.put("a2","v2");
        mt.put("a3","v3");
        mt.delete("a2");
        log.data("插入数据", "a1, a2, a3");
        log.data("删除数据", "a2");
        
        log.step("执行闭区间查询[a1,a3]");
        List<KeyValue> inc = mt.getRange("a1","a3", true, true);
        log.data("返回条目数", inc.size());
        log.data("返回的键", inc.get(0).getKey() + ", " + inc.get(1).getKey());
        Assert.assertEquals(2, inc.size());
        Assert.assertEquals("a1", inc.get(0).getKey());
        Assert.assertEquals("a3", inc.get(1).getKey());
        log.assertSuccess("闭区间查询正确排除了已删除的a2");
        
        log.step("执行开区间查询(a1,a3)");
        List<KeyValue> exc = mt.getRange("a1","a3", false, false);
        log.data("返回条目数", exc.size());
        Assert.assertEquals(0, exc.size());
        log.assertSuccess("开区间查询正确排除了边界和删除项");
        log.pass();
    }
}
