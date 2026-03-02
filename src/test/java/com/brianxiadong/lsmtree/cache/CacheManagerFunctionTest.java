package com.brianxiadong.lsmtree.cache;

import com.brianxiadong.lsmtree.KeyValue;
import com.brianxiadong.lsmtree.TestLogger;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;

/**
 * 缓存管理器功能测试
 * 验证 CacheManager 及其底层实现 (LRU/LFU) 的正确性
 */
public class CacheManagerFunctionTest {

    @Test
    public void testLRUCacheEviction() {
        TestLogger log = new TestLogger("LRU 缓存驱逐测试");
        log.start("验证 LRU 缓存是否按最近最少使用原则驱逐数据");

        // 创建容量为 3 的 LRU 缓存
        InternalCache<String, String> lru = new LRUCache<>(3);
        log.data("缓存容量", 3);

        log.step("填充数据 A, B, C");
        lru.put("A", "valA");
        lru.put("B", "valB");
        lru.put("C", "valC");

        log.step("访问 A (使其成为最近使用)");
        lru.get("A");

        log.step("插入新数据 D (触发驱逐)");
        lru.put("D", "valD");

        log.step("验证驱逐结果");
        // 期望驱逐 B (因为 A 刚被访问，C 是最新插入，B 是最旧且未被访问)
        // 访问顺序: put A -> put B -> put C -> get A -> put D
        // 状态变化:
        // [A]
        // [A, B]
        // [A, B, C]
        // [B, C, A] (get A)
        // [C, A, D] (put D, evict B)
        
        String valA = lru.get("A");
        String valB = lru.get("B");
        String valC = lru.get("C");
        String valD = lru.get("D");

        log.data("A", valA);
        log.data("B", valB);
        log.data("C", valC);
        log.data("D", valD);

        Assert.assertNotNull("A 应该存在", valA);
        Assert.assertNull("B 应该被驱逐", valB);
        Assert.assertNotNull("C 应该存在", valC);
        Assert.assertNotNull("D 应该存在", valD);
        
        log.assertSuccess("LRU 驱逐逻辑正确");
        log.pass();
    }

    @Test
    public void testLFUCacheEviction() {
        TestLogger log = new TestLogger("LFU 缓存驱逐测试");
        log.start("验证 LFU 缓存是否按最少使用频率驱逐数据");

        // 创建容量为 3 的 LFU 缓存
        InternalCache<String, String> lfu = new LFUCache<>(3);
        log.data("缓存容量", 3);

        log.step("填充数据 A, B, C");
        lfu.put("A", "valA");
        lfu.put("B", "valB");
        lfu.put("C", "valC");
        // 初始频率: A=1, B=1, C=1

        log.step("增加 A 和 C 的频率");
        lfu.get("A"); // A=2
        lfu.get("C"); // C=2
        // 状态: A=2, B=1, C=2

        log.step("插入新数据 D (触发驱逐)");
        lfu.put("D", "valD");
        // 应该驱逐频率最低的 B

        log.step("验证驱逐结果");
        String valA = lfu.get("A");
        String valB = lfu.get("B");
        String valC = lfu.get("C");
        String valD = lfu.get("D");

        log.data("A", valA);
        log.data("B", valB);
        log.data("C", valC);
        log.data("D", valD);

        Assert.assertNotNull("A 应该存在", valA);
        Assert.assertNull("B 应该被驱逐", valB);
        Assert.assertNotNull("C 应该存在", valC);
        Assert.assertNotNull("D 应该存在", valD);

        log.assertSuccess("LFU 驱逐逻辑正确");
        log.pass();
    }

    @Test
    public void testCacheManagerTypeSafety() throws CacheException {
        TestLogger log = new TestLogger("CacheManager 类型安全测试");
        log.start("验证 CacheManager 对类型的检查");

        CacheManager manager = new CacheManagerImpl(10, 10, CacheStrategy.LRU);

        log.step("尝试将 Block 放入 Row Cache (应失败)");
        try {
            manager.put("key", new Block("id", new HashMap<>()), CacheType.ROW);
            Assert.fail("应抛出异常");
        } catch (CacheException e) {
            log.assertExpectedFailure("捕获预期异常: " + e.getMessage());
        }

        log.step("尝试将 KeyValue 放入 Block Cache (应失败)");
        try {
            manager.put("key", new KeyValue("k", "v"), CacheType.BLOCK);
            Assert.fail("应抛出异常");
        } catch (CacheException e) {
            log.assertExpectedFailure("捕获预期异常: " + e.getMessage());
        }

        log.pass();
    }
    
    @Test
    public void testTTL() throws InterruptedException {
        TestLogger log = new TestLogger("TTL 过期测试");
        log.start("验证缓存 TTL 机制");
        
        LRUCache<String, String> cache = new LRUCache<>(10);
        cache.setDefaultTTLMillis(100); // 100ms TTL
        
        log.step("写入数据");
        cache.put("key", "value");
        Assert.assertEquals("value", cache.get("key"));
        
        log.step("等待过期 (150ms)");
        Thread.sleep(150);
        
        log.step("验证数据已过期");
        String val = cache.get("key");
        log.data("获取结果", val);
        Assert.assertNull(val);
        
        log.assertSuccess("TTL 过期机制生效");
        log.pass();
    }
}
