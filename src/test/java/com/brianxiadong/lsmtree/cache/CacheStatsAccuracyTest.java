package com.brianxiadong.lsmtree.cache;

import com.brianxiadong.lsmtree.KeyValue;
import com.brianxiadong.lsmtree.TestLogger;
import org.junit.Assert;
import org.junit.Test;

/**
 * 缓存统计准确性测试类
 * 测试缓存命中/未命中/淘汰计数器
 */
public class CacheStatsAccuracyTest {
    @Test
    public void testHitMissEvictCounters() throws Exception {
        TestLogger log = new TestLogger("缓存统计准确性测试");
        log.start("测试缓存命中/未命中/淘汰计数器");
        
        log.step("创建缓存管理器（容量=2）");
        CacheManagerImpl cm = new CacheManagerImpl(2, 1, CacheStrategy.LRU);
        log.data("容量", 2);
        
        log.step("验证初始计数器为0");
        Assert.assertEquals(0, cm.getStats(CacheType.ROW).getHits());
        Assert.assertEquals(0, cm.getStats(CacheType.ROW).getMisses());
        log.assertSuccess("初始计数器为0");
        
        log.step("测试未命中");
        Assert.assertNull(cm.get("a", CacheType.ROW));
        log.data("miss计数", cm.getStats(CacheType.ROW).getMisses());
        
        log.step("插入数据a和b");
        cm.put("a", new KeyValue("a","1"), CacheType.ROW);
        cm.put("b", new KeyValue("b","2"), CacheType.ROW);
        log.data("插入", "a=1, b=2");
        
        log.step("测试命中");
        Assert.assertNotNull(cm.get("a", CacheType.ROW));
        log.data("hit计数", cm.getStats(CacheType.ROW).getHits());
        
        log.step("插入c触发淘汰");
        cm.put("c", new KeyValue("c","3"), CacheType.ROW);
        
        long hits = cm.getStats(CacheType.ROW).getHits();
        long misses = cm.getStats(CacheType.ROW).getMisses();
        long evicts = cm.getStats(CacheType.ROW).getEvictions();
        log.data("最终hits", hits);
        log.data("最终misses", misses);
        log.data("最终evicts", evicts);
        
        Assert.assertTrue(hits >= 1);
        Assert.assertTrue(misses >= 1);
        Assert.assertTrue(evicts >= 1);
        log.assertSuccess("统计计数器正确");
        log.pass();
    }
}