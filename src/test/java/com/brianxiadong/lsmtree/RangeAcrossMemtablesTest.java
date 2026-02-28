package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 跨MemTable范围查询测试类
 * 测试跨越不可变和活跃MemTable的范围查询
 */
public class RangeAcrossMemtablesTest {
    @Test
    public void testRangeSpanningImmutableAndActive() throws Exception {
        TestLogger log = new TestLogger("跨MemTable范围查询测试");
        log.start("测试跨越不可变和活跃MemTable的范围查询");
        
        log.step("创建LSMTree（阈值=2）");
        LSMTree tree = new LSMTree(TestConfig.getFunctionalTestDataPath("range-immut"), 2);
        log.data("MemTable阈值", 2);
        
        log.step("插入a1和a2触发刷盘，然后插入a3");
        tree.put("a1","v1");
        tree.put("a2","v2"); // flush to level0
        tree.put("a3","v3"); // active
        log.data("插入数据", "a1, a2（已刷盘）, a3（活跃）");
        
        log.step("执行范围查询[a1,a3]");
        Iterator<KeyValue> it = tree.range("a1","a3", true, true);
        List<String> keys = new ArrayList<>();
        while (it.hasNext()) keys.add(it.next().getKey());
        log.data("返回的keys", keys);
        
        Assert.assertArrayEquals(new String[]{"a1","a2","a3"}, keys.toArray(new String[0]));
        log.assertSuccess("正确合并了刷盘和活跃MemTable的数据");
        tree.close();
        log.pass();
    }
}

