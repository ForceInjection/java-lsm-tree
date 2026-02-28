package com.brianxiadong.lsmtree.cache;

import com.brianxiadong.lsmtree.KeyValue;
import com.brianxiadong.lsmtree.TestLogger;
import org.junit.Assert;
import org.junit.Test;

/**
 * 缓存容量调整测试类
 * 测试动态调整缓存容量的功能
 */
public class CacheCapacityAdjustTest {
    @Test
    public void testAdjustCapacityDoesNotBreakService() throws Exception {
        TestLogger log = new TestLogger("缓存容量调整测试");
        log.start("测试动态调整缓存容量");
        
        log.step("创建缓存管理器（容量=3）");
        CacheManagerImpl cm = new CacheManagerImpl(3, 1, CacheStrategy.LRU);
        log.data("初始容量", 3);
        
        log.step("插入3条数据a,b,c");
        cm.put("a", new KeyValue("a","1", System.currentTimeMillis(), false), CacheType.ROW);
        cm.put("b", new KeyValue("b","2", System.currentTimeMillis(), false), CacheType.ROW);
        cm.put("c", new KeyValue("c","3", System.currentTimeMillis(), false), CacheType.ROW);
        log.data("插入数据", "a=1, b=2, c=3");
        
        log.step("调整容量为2");
        cm.adjustCapacity(CacheType.ROW, 2);
        log.data("新容量", 2);
        
        log.step("验证只剩2条数据");
        Object oa = cm.get("a", CacheType.ROW);
        Object ob = cm.get("b", CacheType.ROW);
        Object oc = cm.get("c", CacheType.ROW);
        int alive = (oa!=null?1:0) + (ob!=null?1:0) + (oc!=null?1:0);
        log.data("存活条目数", alive);
        Assert.assertEquals(2, alive);
        log.assertSuccess("容量缩小后正确淘汰条目");
        
        log.step("调整容量为4并插入d");
        cm.adjustCapacity(CacheType.ROW, 4);
        cm.put("d", new KeyValue("d","4", System.currentTimeMillis(), false), CacheType.ROW);
        Assert.assertNotNull(cm.get("d", CacheType.ROW));
        log.assertSuccess("容量扩大后正常工作");
        log.pass();
    }
}