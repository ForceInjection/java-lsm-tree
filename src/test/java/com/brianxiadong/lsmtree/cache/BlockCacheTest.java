package com.brianxiadong.lsmtree.cache;

import com.brianxiadong.lsmtree.KeyValue;
import com.brianxiadong.lsmtree.TestLogger;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Block Cache 测试类
 * 测试块缓存的填充、读取和失效功能
 */
public class BlockCacheTest {
    @Test
    public void testPopulateBlockForKeysAndGet() throws Exception {
        TestLogger log = new TestLogger("Block填充和读取测试");
        log.start("测试填充块缓存并读取数据");
        
        log.step("创建CacheManager（容量=10，策略=LRU）");
        CacheManagerImpl cm = new CacheManagerImpl(10, 10, CacheStrategy.LRU);
        
        log.step("添加测试数据");
        List<KeyValue> list = new ArrayList<>();
        list.add(new KeyValue("k01","v1"));
        list.add(new KeyValue("k02","v2"));
        list.add(new KeyValue("k03","v3"));
        log.data("数据条数", list.size());
        
        log.step("填充Block缓存");
        cm.populateBlockForKeys(list);
        
        log.step("读取并验证Block");
        String bid = cm.computeBlockId("k02");
        log.data("计算的BlockId", bid);
        Object obj = cm.get(bid, CacheType.BLOCK);
        Assert.assertTrue(obj instanceof Block);
        log.assertSuccess("返回的是Block实例");
        
        Block b = (Block) obj;
        log.data("Block中的条目数", b.getEntries().size());
        Assert.assertTrue(b.getEntries().containsKey("k02"));
        log.assertSuccess("Block中包含key k02");
        log.pass();
    }

    @Test
    public void testBlockInvalidate() throws Exception {
        TestLogger log = new TestLogger("Block失效测试");
        log.start("测试Block缓存的失效功能");
        
        log.step("创建CacheManager并添加数据");
        CacheManagerImpl cm = new CacheManagerImpl(10, 10, CacheStrategy.LRU);
        List<KeyValue> list = new ArrayList<>();
        list.add(new KeyValue("k01","v1"));
        list.add(new KeyValue("k02","v2"));
        cm.populateBlockForKeys(list);
        
        String bid = cm.computeBlockId("k02");
        log.data("BlockId", bid);
        
        log.step("验证Block存在");
        Object obj = cm.get(bid, CacheType.BLOCK);
        log.data("读取结果", obj != null ? "Block实例" : "null");
        Assert.assertNotNull(obj);
        log.assertSuccess("Block存在");
        
        log.step("执行失效操作");
        cm.invalidate(bid, CacheType.BLOCK);
        Object after = cm.get(bid, CacheType.BLOCK);
        log.data("失效后的读取结果", after != null ? "Block实例" : "null");
        Assert.assertNull(after);
        log.assertSuccess("失效后Block被清除");
        log.pass();
    }
}