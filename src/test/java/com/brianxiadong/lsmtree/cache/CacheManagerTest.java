package com.brianxiadong.lsmtree.cache;

import com.brianxiadong.lsmtree.KeyValue;
import com.brianxiadong.lsmtree.TestLogger;
import org.junit.Assert;
import org.junit.Test;

/**
 * Cache Manager测试类
 * 测试缓存管理器的LRU/LFU策略和TTL功能
 */
public class CacheManagerTest {
    @Test
    public void testBasicPutGetInvalidateLRU() throws Exception {
        TestLogger log = new TestLogger("Cache Manager LRU基本操作测试");
        log.start("测试LRU缓存的put/get/invalidate操作");
        
        log.step("创建LRU缓存管理器（容量=2）");
        CacheManagerImpl cm = new CacheManagerImpl(2, 2, CacheStrategy.LRU);
        log.data("策略", "LRU");
        log.data("容量", 2);
        
        log.step("插入k1和k2");
        KeyValue kv1 = new KeyValue("k1","v1", System.currentTimeMillis(), false);
        KeyValue kv2 = new KeyValue("k2","v2", System.currentTimeMillis(), false);
        cm.put("k1", kv1, CacheType.ROW);
        cm.put("k2", kv2, CacheType.ROW);
        log.data("插入数据", "k1, k2");
        
        Assert.assertNotNull(cm.get("k1", CacheType.ROW));
        Assert.assertNotNull(cm.get("k2", CacheType.ROW));
        log.assertSuccess("k1和k2都存在缓存中");
        
        log.step("插入k3触发淘汰");
        cm.put("k3", new KeyValue("k3","v3", System.currentTimeMillis(), false), CacheType.ROW);
        cm.get("k3", CacheType.ROW);
        CacheStats stats = cm.getStats(CacheType.ROW);
        log.data("淘汰次数", stats.getEvictions());
        Assert.assertTrue(stats.getEvictions() >= 1);
        log.assertSuccess("LRU淘汰生效");
        
        log.step("测试invalidate");
        cm.invalidate("k3", CacheType.ROW);
        Assert.assertNull(cm.get("k3", CacheType.ROW));
        log.assertSuccess("invalidate成功");
        log.pass();
    }

    @Test
    public void testLFUBehavior() throws Exception {
        TestLogger log = new TestLogger("Cache Manager LFU行为测试");
        log.start("测试LFU缓存的频率淘汰策略");
        
        log.step("创建LFU缓存管理器");
        CacheManagerImpl cm = new CacheManagerImpl(2, 2, CacheStrategy.LFU);
        log.data("策略", "LFU");
        
        log.step("插入a和b，然后访问a");
        cm.put("a", new KeyValue("a","1", System.currentTimeMillis(), false), CacheType.ROW);
        cm.put("b", new KeyValue("b","2", System.currentTimeMillis(), false), CacheType.ROW);
        cm.get("a", CacheType.ROW);  // 增加a的访问频率
        log.data("插入", "a, b");
        log.data("访问", "a（增加频率）");
        
        log.step("插入c触发淘汰");
        cm.put("c", new KeyValue("c","3", System.currentTimeMillis(), false), CacheType.ROW);
        
        Object oa = cm.get("a", CacheType.ROW);
        Object ob = cm.get("b", CacheType.ROW);
        Object oc = cm.get("c", CacheType.ROW);
        log.data("a存在", oa != null);
        log.data("b存在", ob != null);
        log.data("c存在", oc != null);
        
        Assert.assertNotNull(oa);
        int alive = (oa!=null?1:0) + (ob!=null?1:0) + (oc!=null?1:0);
        log.data("存活条目数", alive);
        Assert.assertEquals(2, alive);
        log.assertSuccess("LFU正确淘汰低频条目");
        log.pass();
    }

    @Test
    public void testTTLExpiry() throws Exception {
        TestLogger log = new TestLogger("Cache TTL过期测试");
        log.start("测试缓存条目的TTL过期功能");
        
        log.step("创建缓存管理器并设置TTL=5ms");
        CacheManagerImpl cm = new CacheManagerImpl(10, 10, CacheStrategy.LRU);
        cm.setRowCacheTTLMillis(5);
        log.data("TTL", "5ms");
        
        log.step("插入数据x");
        cm.put("x", new KeyValue("x","vx", System.currentTimeMillis(), false), CacheType.ROW);
        log.data("插入", "x=vx");
        
        log.step("等待20ms让TTL过期");
        Thread.sleep(20);
        
        Object result = cm.get("x", CacheType.ROW);
        log.data("过期后获取结果", result);
        Assert.assertNull(result);
        log.assertSuccess("TTL过期后条目被正确移除");
        log.pass();
    }
}