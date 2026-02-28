package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * 分区范围查询测试类
 * 测试跨分区的范围查询功能
 */
public class PartitioningRangeTest {
    @Test
    public void testRangeAcrossPartitions() throws Exception {
        TestLogger log = new TestLogger("跨分区范围查询测试");
        log.start("测试跨越多个分区的范围查询");
        
        log.step("创建分区策略（边界：b, d, f）");
        List<String> bounds = Arrays.asList("b","d","f"); // 4 partitions: (-,b],[b,d],[d,f],[f,+)
        PartitionStrategy s = new RangePartitionStrategy(bounds);
        log.data("分区边界", bounds);
        log.data("分区数", 4);
        
        log.step("创建分区LSMTree并插入a-h数据");
        try (PartitionedLSMTree tree = new PartitionedLSMTree(TestConfig.getFunctionalTestDataPath("partitioning"), 4, 10, s)) {
            for (char c = 'a'; c <= 'h'; c++) {
                tree.put(""+c, "v"+c);
            }
            log.data("插入数据", "a-h（8条）");
            
            log.step("执行范围查询[a,h]");
            Iterator<KeyValue> it = tree.range("a","h", true, true);
            int count = 0;
            while (it.hasNext()) {
                it.next();
                count++;
            }
            log.data("返回条目数", count);
            
            Assert.assertEquals(8, count);
            log.assertSuccess("跨分区查询返回所有数据");
        }
        log.pass();
    }
}

