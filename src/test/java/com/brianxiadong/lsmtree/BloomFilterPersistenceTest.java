package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

/**
 * Bloom Filter持久化测试类
 * 测试布隆过滤器的序列化和反序列化功能
 */
public class BloomFilterPersistenceTest {
    @Test
    public void testToFromByteArray() {
        TestLogger log = new TestLogger("Bloom Filter持久化测试");
        log.start("测试布隆过滤器的序列化和反序列化");
        
        log.step("创建Bloom Filter（容量=100，误判率=0.01）");
        BloomFilter bf = new BloomFilter(100, 0.01);
        log.data("容量", 100);
        log.data("误判率", "1%");
        
        log.step("添加测试元素");
        bf.add("a");
        bf.add("b");
        log.data("添加元素", "a, b");
        
        log.step("序列化为字节数组");
        byte[] bytes = bf.toByteArray();
        log.data("字节数组大小", bytes.length + " bytes");
        
        log.step("从字节数组恢复");
        BloomFilter restored = BloomFilter.fromByteArray(bytes, 100, 7);
        
        log.step("验证恢复后的过滤器");
        boolean hasA = restored.mightContain("a");
        boolean hasB = restored.mightContain("b");
        boolean hasNotExist = restored.mightContain("not-exist");
        log.data("包含'a'", hasA);
        log.data("包含'b'", hasB);
        log.data("包含'not-exist'", hasNotExist);
        
        Assert.assertTrue(hasA);
        Assert.assertTrue(hasB);
        Assert.assertFalse(hasNotExist);
        log.assertSuccess("序列化和反序列化正确");
        log.pass();
    }
}
