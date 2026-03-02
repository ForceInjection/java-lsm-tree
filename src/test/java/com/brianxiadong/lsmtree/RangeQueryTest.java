package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 范围查询测试类
 * 测试LSM Tree的范围查询功能，包括闭区间、开区间、反向查询等
 * 
 * 使用 LSMTreeTestBase 提供的统一生命周期管理和工具方法
 */
public class RangeQueryTest extends LSMTreeTestBase {
    
    /**
     * 使用小容量 MemTable 以便快速触发刷盘和多层结构
     */
    @Override
    protected int getDefaultMemTableSize() {
        return 3;
    }

    @Test
    public void testRangeInclusive() throws Exception {
        TestLogger log = new TestLogger("闭区间范围查询测试");
        log.start("测试闭区间[a2,a4]的范围查询");
        
        log.step("插入测试数据");
        lsmTree.put("a1", "v1");
        lsmTree.put("a2", "v2");
        lsmTree.put("a3", "v3");
        lsmTree.put("a4", "v4");
        lsmTree.put("a5", "v5");
        log.data("数据条数", 5);
        
        log.step("执行闭区间查询[a2,a4]");
        Iterator<KeyValue> it = lsmTree.range("a2", "a4", true, true);
        List<String> keys = new ArrayList<>();
        while (it.hasNext()) keys.add(it.next().getKey());
        
        log.data("返回条数", keys.size());
        log.data("返回的keys", keys);
        Assert.assertEquals(3, keys.size());
        Assert.assertArrayEquals(new String[]{"a2","a3","a4"}, keys.toArray(new String[0]));
        log.assertSuccess("闭区间查询返回正确的数据");
        log.pass();
    }

    @Test
    public void testRangeExclusiveAndReverse() throws Exception {
        TestLogger log = new TestLogger("开区间和反向查询测试");
        log.start("测试开区间(a2,a4)和反向查询");
        
        log.step("插入测试数据");
        lsmTree.put("a1", "v1");
        lsmTree.put("a2", "v2");
        lsmTree.put("a3", "v3");
        lsmTree.put("a4", "v4");
        lsmTree.put("a5", "v5");
        
        log.step("执行开区间查询(a2,a4)");
        Iterator<KeyValue> it = lsmTree.range("a2", "a4", false, false);
        List<String> keys = new ArrayList<>();
        while (it.hasNext()) keys.add(it.next().getKey());
        log.data("返回条数", keys.size());
        log.data("返回的keys", keys);
        Assert.assertEquals(1, keys.size());
        Assert.assertEquals("a3", keys.get(0));
        log.assertSuccess("开区间查询正确排除了边界");
        
        log.step("执行反向查询[a2,a4]");
        Iterator<KeyValue> rev = lsmTree.rangeReverse("a2", "a4");
        List<String> rkeys = new ArrayList<>();
        while (rev.hasNext()) rkeys.add(rev.next().getKey());
        log.data("反向返回的keys", rkeys);
        Assert.assertArrayEquals(new String[]{"a4","a3","a2"}, rkeys.toArray(new String[0]));
        log.assertSuccess("反向查询返回正确顺序的数据");
        log.pass();
    }

    @Test
    public void testRangeWithDeletes() throws Exception {
        TestLogger log = new TestLogger("包含删除的范围查询测试");
        log.start("测试范围查询正确处理已删除的key");
        
        // 临时使用更大的 MemTable 以避免过早刷盘
        cleanupResources();
        lsmTree = new LSMTree(getTestDataDir(), 10);
        
        log.step("插入测试数据并删除a2");
        lsmTree.put("a1", "v1");
        lsmTree.put("a2", "v2");
        lsmTree.put("a3", "v3");
        lsmTree.delete("a2");
        
        log.step("执行闭区间查询[a1,a3]");
        Iterator<KeyValue> it = lsmTree.range("a1", "a3", true, true);
        List<String> keys = new ArrayList<>();
        while (it.hasNext()) keys.add(it.next().getKey());
        log.data("返回条数", keys.size());
        log.data("返回的keys", keys);
        Assert.assertArrayEquals(new String[]{"a1","a3"}, keys.toArray(new String[0]));
        log.assertSuccess("范围查询正确排除了已删除的key");
        log.pass();
    }
}
