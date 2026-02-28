package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * 分区策略测试类
 * 测试范围分区和一致性哈希分区的映射功能
 */
public class PartitionStrategyTest {
    @Test
    public void testRangePartitionMapping() {
        TestLogger log = new TestLogger("范围分区映射测试");
        log.start("测试RangePartitionStrategy的分区映射");
        
        List<String> bounds = Arrays.asList("b","d","f");
        log.step("创建范围分区策略");
        log.data("分区边界", bounds);
        RangePartitionStrategy s = new RangePartitionStrategy(bounds);
        
        log.step("测试单个key的分区映射");
        int p0 = s.getPartition("a", 4);
        int p1 = s.getPartition("c", 4);
        int p2 = s.getPartition("e", 4);
        int p3 = s.getPartition("z", 4);
        log.data("key='a' -> 分区", p0);
        log.data("key='c' -> 分区", p1);
        log.data("key='e' -> 分区", p2);
        log.data("key='z' -> 分区", p3);
        
        Assert.assertEquals(0, p0);
        Assert.assertEquals(1, p1);
        Assert.assertEquals(2, p2);
        Assert.assertEquals(3, p3);
        log.assertSuccess("单个key分区映射正确");
        
        log.step("测试范围查询的分区映射");
        List<Integer> ps = s.getPartitionsForRange("a","e",4);
        log.data("范围[a,e]涉及的分区", ps);
        Assert.assertEquals(Arrays.asList(0,1,2), ps);
        log.assertSuccess("范围分区映射正确");
        log.pass();
    }

    @Test
    public void testConsistentHashCoversAll() {
        TestLogger log = new TestLogger("一致性哈希分区测试");
        log.start("测试ConsistentHashPartitionStrategy覆盖所有分区");
        
        log.step("创建一致性哈希分区策略");
        ConsistentHashPartitionStrategy s = new ConsistentHashPartitionStrategy();
        
        log.step("查询范围[a,z]涉及的分区");
        List<Integer> ps = s.getPartitionsForRange("a","z", 8);
        log.data("返回的分区数", ps.size());
        log.data("分区列表", ps);
        
        Assert.assertEquals(8, ps.size());
        for (int i = 0; i < 8; i++) Assert.assertTrue(ps.contains(i));
        log.assertSuccess("一致性哈希正确覆盖所有分区");
        log.pass();
    }
}
