package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 反向范围查询测试类
 * 测试反向顺序的范围查询功能
 */
public class RangeReverseOrderTest {
    @Test
    public void testReverseOrderAndDeletes() throws Exception {
        TestLogger log = new TestLogger("反向范围查询测试");
        log.start("测试反向顺序的范围查询和删除处理");
        
        log.step("创建LSMTree并插入5条数据");
        LSMTree tree = new LSMTree(TestConfig.getFunctionalTestDataPath("range-reverse"), 5);
        for (int i = 1; i <= 5; i++) tree.put("a" + i, "v" + i);
        log.data("插入数据", "a1-a5");
        
        log.step("删除a3");
        tree.delete("a3");
        log.data("删除", "a3");
        
        log.step("执行反向查询[a2,a5]");
        Iterator<KeyValue> it = tree.rangeReverse("a2","a5");
        List<String> keys = new ArrayList<>();
        while (it.hasNext()) keys.add(it.next().getKey());
        log.data("返回的keys", keys);
        
        Assert.assertArrayEquals(new String[]{"a5","a4","a2"}, keys.toArray(new String[0]));
        log.assertSuccess("反向查询正确排除a3并按降序返回");
        tree.close();
        log.pass();
    }
}

