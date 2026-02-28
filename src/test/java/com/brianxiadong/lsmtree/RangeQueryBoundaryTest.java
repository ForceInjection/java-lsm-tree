package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 范围查询边界测试类
 * 测试范围查询的开闭区间和边界情况
 */
public class RangeQueryBoundaryTest {
    @Test
    public void testInclusiveExclusiveBoundaries() throws Exception {
        TestLogger log = new TestLogger("开闭区间边界测试");
        log.start("测试范围查询的开闭区间组合");
        
        LSMTree tree = new LSMTree(TestConfig.getFunctionalTestDataPath("range-boundary"), 100);
        for (int i = 1; i <= 5; i++) tree.put("a" + i, "v" + i);
        log.step("插入数据a1-a5");
        
        log.step("测试[a2,a4]闭区间");
        Iterator<KeyValue> it1 = tree.range("a2", "a4", true, true);
        List<String> k1 = new ArrayList<>();
        while (it1.hasNext()) k1.add(it1.next().getKey());
        log.data("[a2,a4]闭区间返回", k1);
        Assert.assertArrayEquals(new String[]{"a2","a3","a4"}, k1.toArray(new String[0]));
        
        log.step("测试(a2,a4]左开右闭");
        Iterator<KeyValue> it2 = tree.range("a2", "a4", false, true);
        List<String> k2 = new ArrayList<>();
        while (it2.hasNext()) k2.add(it2.next().getKey());
        log.data("(a2,a4]返回", k2);
        Assert.assertArrayEquals(new String[]{"a3","a4"}, k2.toArray(new String[0]));
        
        log.step("测试[a2,a4)左闭右开");
        Iterator<KeyValue> it3 = tree.range("a2", "a4", true, false);
        List<String> k3 = new ArrayList<>();
        while (it3.hasNext()) k3.add(it3.next().getKey());
        log.data("[a2,a4)返回", k3);
        Assert.assertArrayEquals(new String[]{"a2","a3"}, k3.toArray(new String[0]));
        
        log.step("测试(a2,a4)开区间");
        Iterator<KeyValue> it4 = tree.range("a2", "a4", false, false);
        List<String> k4 = new ArrayList<>();
        while (it4.hasNext()) k4.add(it4.next().getKey());
        log.data("(a2,a4)返回", k4);
        Assert.assertArrayEquals(new String[]{"a3"}, k4.toArray(new String[0]));
        
        tree.close();
        log.assertSuccess("开闭区间边界正确");
        log.pass();
    }

    @Test
    public void testEmptyAndFullRanges() throws Exception {
        TestLogger log = new TestLogger("空范围和全范围测试");
        log.start("测试空范围和全范围查询");
        
        LSMTree tree = new LSMTree(TestConfig.getFunctionalTestDataPath("range-empty-full"), 100);
        for (int i = 1; i <= 3; i++) tree.put("a" + i, "v" + i);
        log.data("插入数据", "a1-a3");
        
        log.step("测试空范围[b1,b2]");
        Iterator<KeyValue> it1 = tree.range("b1", "b2", true, true);
        log.data("空范围返回", it1.hasNext() ? "有数据" : "空");
        Assert.assertFalse(it1.hasNext());
        
        log.step("测试全范围[null,null]");
        Iterator<KeyValue> it2 = tree.range(null, null, true, true);
        List<String> all = new ArrayList<>();
        while (it2.hasNext()) all.add(it2.next().getKey());
        log.data("全范围返回", all);
        Assert.assertArrayEquals(new String[]{"a1","a2","a3"}, all.toArray(new String[0]));
        
        tree.close();
        log.assertSuccess("空范围和全范围正确");
        log.pass();
    }

    @Test
    public void testTombstoneVisibilityInRange() throws Exception {
        TestLogger log = new TestLogger("范围查询墓碑可见性测试");
        log.start("测试范围查询正确排除已删除数据");
        
        LSMTree tree = new LSMTree(TestConfig.getFunctionalTestDataPath("range-tombstone"), 100);
        tree.put("a1","v1");
        tree.put("a2","v2");
        tree.delete("a2");
        log.data("插入", "a1=v1, a2=v2");
        log.data("删除", "a2");
        
        log.step("执行范围查询[a1,a3]");
        Iterator<KeyValue> it = tree.range("a1","a3", true, true);
        List<String> keys = new ArrayList<>();
        while (it.hasNext()) keys.add(it.next().getKey());
        log.data("返回的keys", keys);
        
        Assert.assertArrayEquals(new String[]{"a1"}, keys.toArray(new String[0]));
        log.assertSuccess("正确排除了已删除的a2");
        tree.close();
        log.pass();
    }
}

