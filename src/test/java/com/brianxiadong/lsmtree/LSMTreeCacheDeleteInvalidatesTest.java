package com.brianxiadong.lsmtree;

import com.brianxiadong.lsmtree.cache.CacheManagerImpl;
import com.brianxiadong.lsmtree.cache.CacheStrategy;
import com.brianxiadong.lsmtree.cache.CacheType;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

/**
 * LSM Tree缓存删除失效测试类
 * 测试删除操作对缓存的影响
 */
public class LSMTreeCacheDeleteInvalidatesTest {
    @Test
    public void testDeleteReflectsInCacheAndGetReturnsNull() throws Exception {
        TestLogger log = new TestLogger("缓存删除失效测试");
        log.start("测试删除操作正确更新缓存");
        
        String dir = System.getProperty("java.io.tmpdir") + File.separator + "cache_del_" + System.currentTimeMillis();
        LSMTree tree = new LSMTree(dir, 100);
        CacheManagerImpl cm = new CacheManagerImpl(1000, 100, CacheStrategy.LRU);
        tree.setCacheManager(cm);
        log.data("缓存策略", "LRU");
        
        log.step("插入数据k=v");
        tree.put("k","v");
        log.data("插入", "k=v");
        Assert.assertEquals("v", tree.get("k"));
        log.assertSuccess("读取成功");
        
        log.step("删除k");
        tree.delete("k");
        log.data("删除", "k");
        
        log.step("验证删除后的get返回null");
        Assert.assertNull(tree.get("k"));
        log.assertSuccess("get返回null");
        
        log.step("验证缓存中的状态");
        Object obj = cm.get("k", CacheType.ROW);
        log.data("缓存中存在", obj != null);
        Assert.assertTrue(obj instanceof KeyValue);
        log.data("缓存项是否为删除标记", ((KeyValue) obj).isDeleted());
        Assert.assertTrue(((KeyValue) obj).isDeleted());
        log.assertSuccess("缓存正确标记为删除状态");
        tree.close();
        log.pass();
    }
}