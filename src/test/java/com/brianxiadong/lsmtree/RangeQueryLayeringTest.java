package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 范围查询分层测试类
 * 测试跨MemTable和SSTable的范围查询
 */
public class RangeQueryLayeringTest {
    @Test
    public void testAcrossMemtableAndSSTable() throws Exception {
        TestLogger log = new TestLogger("跨层范围查询测试");
        log.start("测试跨MemTable和SSTable的范围查询");
        
        log.step("创建LSMTree（阈值=3）并插入5条数据触发刷盘");
        LSMTree tree = new LSMTree(TestConfig.getFunctionalTestDataPath("range-layering"), 3);
        tree.put("a1","v1");
        tree.put("a2","v2");
        tree.put("a3","v3");
        tree.put("a4","v4");
        tree.put("a5","v5");
        log.data("MemTable阈值", 3);
        log.data("插入数据", "a1-a5");
        
        log.step("执行范围查询[a1,a5]");
        Iterator<KeyValue> it = tree.range("a1","a5", true, true);
        List<String> keys = new ArrayList<>();
        while (it.hasNext()) keys.add(it.next().getKey());
        log.data("返回的keys", keys);
        
        Assert.assertArrayEquals(new String[]{"a1","a2","a3","a4","a5"}, keys.toArray(new String[0]));
        log.assertSuccess("正确合并多层存储的数据");
        tree.close();
        log.pass();
    }

    @Test
    public void testLatestVersionWins() throws Exception {
        TestLogger log = new TestLogger("最新版本优先测试");
        log.start("测试范围查询返回最新版本");
        
        log.step("创建LSMTree并插入更新数据");
        LSMTree tree = new LSMTree(TestConfig.getFunctionalTestDataPath("range-latest"), 2);
        tree.put("a1","v1");
        tree.put("a2","v2");
        tree.put("a1","v1_new");
        log.data("插入", "a1=v1, a2=v2, a1=v1_new");
        
        log.step("执行范围查询[a1,a2]");
        Iterator<KeyValue> it = tree.range("a1","a2", true, true);
        List<String> vals = new ArrayList<>();
        while (it.hasNext()) vals.add(it.next().getValue());
        log.data("返回的values", vals);
        
        Assert.assertArrayEquals(new String[]{"v1_new","v2"}, vals.toArray(new String[0]));
        log.assertSuccess("返回最新版本v1_new");
        tree.close();
        log.pass();
    }
}

