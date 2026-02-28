package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

/**
 * 范围分区起始大于终止测试类
 * 测试start > end时的分区处理
 */
public class RangePartitionStartGreaterEndTest {
    @Test
    public void testStartGreaterThanEndPartitionsAscending() {
        TestLogger log = new TestLogger("起始大于终止分区测试");
        log.start("测试start > end时返回升序分区");
        
        log.step("创建范围分区策略（边界：m, t）");
        RangePartitionStrategy strat = new RangePartitionStrategy(Arrays.asList("m","t"));
        log.data("分区边界", "[m, t]");
        
        log.step("测试start > end的情况");
        log.data("start", "z");
        log.data("end", "a");
        log.data("期望结果", "升序返回所有分区");
        
        log.step("执行分区查询");
        Assert.assertEquals(Arrays.asList(0,1,2), strat.getPartitionsForRange("z","a",3));
        log.data("返回的分区", Arrays.asList(0,1,2));
        log.assertSuccess("正确返回升序分区");
        log.pass();
    }
}
