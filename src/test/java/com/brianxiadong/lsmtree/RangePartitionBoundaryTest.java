package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * 范围分区边界测试类
 * 测试范围分区策略的边界情况
 */
public class RangePartitionBoundaryTest {
    @Test
    public void testGetPartitionAndRangePartitions() {
        TestLogger log = new TestLogger("范围分区边界测试");
        log.start("测试单个key分区映射和范围分区映射");
        
        log.step("创建范围分区策略（边界：b, d）");
        RangePartitionStrategy strat = new RangePartitionStrategy(Arrays.asList("b","d"));
        log.data("分区边界", "[b, d]");
        
        log.step("测试单个key的分区映射");
        Assert.assertEquals(0, strat.getPartition("a", 3));
        Assert.assertEquals(0, strat.getPartition("b", 3));
        Assert.assertEquals(1, strat.getPartition("c", 3));
        Assert.assertEquals(1, strat.getPartition("d", 3));
        Assert.assertEquals(2, strat.getPartition("e", 3));
        log.data("a->分区0, b->分区0, c->分区1, d->分区1, e->分区2", "正确");
        
        log.step("测试范围查询涉及的分区");
        List<Integer> parts1 = strat.getPartitionsForRange("a","c",3);
        log.data("[a,c]涉及分区", parts1);
        Assert.assertEquals(Arrays.asList(0,1), parts1);
        
        List<Integer> parts2 = strat.getPartitionsForRange(null,"a",3);
        log.data("[null,a]涉及分区", parts2);
        Assert.assertEquals(Arrays.asList(0), parts2);
        
        List<Integer> parts3 = strat.getPartitionsForRange("e",null,3);
        log.data("[e,null]涉及分区", parts3);
        Assert.assertEquals(Arrays.asList(2), parts3);
        log.assertSuccess("范围分区映射正确");
        log.pass();
    }

    @Test
    public void testPartitionedRangeQuery() throws Exception {
        TestLogger log = new TestLogger("分区范围查询测试");
        log.start("测试分区LSMTree的范围查询");
        
        RangePartitionStrategy strat = new RangePartitionStrategy(Arrays.asList("b","d"));
        try (PartitionedLSMTree db = new PartitionedLSMTree(TestConfig.getFunctionalTestDataPath("part-range"), 3, 10, strat)) {
            log.step("插入数据a,b,c,d,e");
            for (String k : Arrays.asList("a","b","c","d","e")) db.put(k, k);
            log.data("插入数据", "a, b, c, d, e");
            
            log.step("执行范围查询[a,e]");
            Iterator<KeyValue> it = db.range("a","e", true, true);
            StringBuilder sb = new StringBuilder();
            while (it.hasNext()) sb.append(it.next().getKey());
            log.data("返回的keys", sb.toString());
            Assert.assertEquals("abcde", sb.toString());
            log.assertSuccess("范围查询正确返回所有数据");
        }
        log.pass();
    }
}

