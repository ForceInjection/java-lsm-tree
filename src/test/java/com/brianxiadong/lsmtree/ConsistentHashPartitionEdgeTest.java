package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.Iterator;

/**
 * 一致性哈希分区边界测试类
 * 测试一致性哈希分区策略的边界情况
 */
public class ConsistentHashPartitionEdgeTest {
    @Test
    public void testGetPartitionsForRangeReturnsAll() {
        TestLogger log = new TestLogger("一致性哈希范围分区测试");
        log.start("测试范围查询返回所有分区");
        
        ConsistentHashPartitionStrategy strat = new ConsistentHashPartitionStrategy();
        log.step("查询[a,z]涉及的分区（分区数=4）");
        int size = strat.getPartitionsForRange("a","z",4).size();
        log.data("返回分区数", size);
        Assert.assertEquals(4, size);
        log.assertSuccess("返回所有分区");
        log.pass();
    }

    @Test
    public void testStablePartitionMapping() {
        TestLogger log = new TestLogger("一致性哈希稳定映射测试");
        log.start("测试同一key多次映射结果稳定");
        
        ConsistentHashPartitionStrategy strat = new ConsistentHashPartitionStrategy();
        log.step("对key-123进行两次分区映射");
        int p1 = strat.getPartition("key-123", 8);
        int p2 = strat.getPartition("key-123", 8);
        log.data("第一次映射", p1);
        log.data("第二次映射", p2);
        Assert.assertEquals(p1, p2);
        Assert.assertTrue(p1 >= 0 && p1 < 8);
        log.assertSuccess("映射结果稳定一致");
        log.pass();
    }

    @Test
    public void testPartitionedOperations() throws Exception {
        TestLogger log = new TestLogger("一致性哈希分区操作测试");
        log.start("测试分区LSMTree的基本操作");
        
        ConsistentHashPartitionStrategy strat = new ConsistentHashPartitionStrategy();
        try (PartitionedLSMTree db = new PartitionedLSMTree(TestConfig.getFunctionalTestDataPath("hash-part"), 4, 10, strat)) {
            log.step("插入数据");
            db.put("u1","v1");
            db.put("u2","v2");
            log.data("插入", "u1=v1, u2=v2");
            
            log.step("读取数据");
            Assert.assertEquals("v1", db.get("u1"));
            Assert.assertEquals("v2", db.get("u2"));
            log.assertSuccess("读取正确");
            
            log.step("范围查询");
            Iterator<KeyValue> it = db.range(null, null, true, true);
            int c = 0;
            while (it.hasNext()) { it.next(); c++; }
            log.data("返回条目数", c);
            Assert.assertTrue(c >= 2);
            log.assertSuccess("范围查询返回数据");
        }
        log.pass();
    }
}

