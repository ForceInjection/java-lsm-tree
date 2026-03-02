package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

/**
 * Bloom Filter 负数 Hash 测试类
 * 验证修复后的 Bloom Filter 能正确处理 hashCode 为负数的键
 */
public class BloomFilterNegativeHashTest {

    @Test
    public void testNegativeHashHandling() {
        TestLogger log = new TestLogger("Bloom Filter 负数 Hash 测试");
        log.start("测试 Bloom Filter 处理负数 Hash 的能力");

        // "polygenelubricants" 的 hashCode 在 Java 中是 -2147483648 (Integer.MIN_VALUE)
        // 这是一个极端的边界情况，Math.abs(Integer.MIN_VALUE) 仍然是负数
        String negativeHashKey = "polygenelubricants";
        log.data("测试 Key", negativeHashKey);
        log.data("Key HashCode", negativeHashKey.hashCode());

        BloomFilter bf = new BloomFilter(100, 0.01);

        log.step("添加负数 Hash 的 Key");
        try {
            bf.add(negativeHashKey);
            log.pass();
        } catch (IndexOutOfBoundsException e) {
            log.assertExpectedFailure("添加负数 Hash Key 时抛出异常: " + e.getMessage());
            throw e;
        }

        log.step("查询负数 Hash 的 Key");
        try {
            boolean result = bf.mightContain(negativeHashKey);
            log.data("查询结果", result);
            Assert.assertTrue("刚刚添加的 Key 应该返回 true", result);
            log.pass();
        } catch (IndexOutOfBoundsException e) {
            log.assertExpectedFailure("查询负数 Hash Key 时抛出异常: " + e.getMessage());
            throw e;
        }
        
        log.assertSuccess("Bloom Filter 正确处理了负数 Hash");
    }
    
    @Test
    public void testOtherNegativeKeys() {
        TestLogger log = new TestLogger("普通负数 Hash 测试");
        BloomFilter bf = new BloomFilter(100, 0.01);
        int negativeCount = 0;
        
        for (int i = 0; i < 1000; i++) {
            String k = "key-" + i;
            if (k.hashCode() < 0) {
                negativeCount++;
                bf.add(k);
                Assert.assertTrue(bf.mightContain(k));
            }
        }
        log.data("测试的负数 Hash Key 数量", negativeCount);
        log.pass();
    }
}
