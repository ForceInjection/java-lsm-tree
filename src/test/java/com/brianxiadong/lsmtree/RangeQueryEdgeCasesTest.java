package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 范围查询边界情况测试类
 * 测试范围查询的边界条件和异常情况
 */
public class RangeQueryEdgeCasesTest {
    @Test(expected = IllegalArgumentException.class)
    public void testStartGreaterThanEnd() throws Exception {
        TestLogger log = new TestLogger("起始大于终止异常测试");
        log.start("测试起始key大于终止key时抛出异常");
        
        log.step("创建LSMTree并尝试范围查询b>a");
        LSMTree tree = new LSMTree(TestConfig.getFunctionalTestDataPath("range-edge1"), 10);
        log.data("起始key", "b");
        log.data("终止key", "a");
        log.data("期望结果", "IllegalArgumentException");
        try {
            tree.range("b", "a", true, true);
        } finally {
            tree.close();
        }
        log.pass();
    }

    @Test
    public void testNullStartOrEnd() throws Exception {
        TestLogger log = new TestLogger("null边界测试");
        log.start("测试起始或终止key为null的范围查询");
        
        LSMTree tree = new LSMTree(TestConfig.getFunctionalTestDataPath("range-edge2"), 100);
        for (int i = 1; i <= 5; i++) tree.put("a" + i, "v" + i);
        log.step("插入数据a1-a5");
        log.data("数据条数", 5);

        log.step("测试null起始（从开头查询）");
        Iterator<KeyValue> it1 = tree.range(null, "a3", true, true);
        List<String> k1 = new ArrayList<>();
        while (it1.hasNext()) k1.add(it1.next().getKey());
        log.data("[null,a3]返回", k1);
        Assert.assertArrayEquals(new String[]{"a1","a2","a3"}, k1.toArray(new String[0]));

        log.step("测试null终止（查询到末尾）");
        Iterator<KeyValue> it2 = tree.range("a3", null, true, true);
        List<String> k2 = new ArrayList<>();
        while (it2.hasNext()) k2.add(it2.next().getKey());
        log.data("[a3,null]返回", k2);
        Assert.assertArrayEquals(new String[]{"a3","a4","a5"}, k2.toArray(new String[0]));

        tree.close();
        log.assertSuccess("null边界处理正确");
        log.pass();
    }
}

