package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

/**
 * LZ4压缩策略分支测试类
 * 测试LZ4压缩的压缩比率计算
 */
public class LZ4CompressionStrategyBranchTest {
    @Test
    public void testCompressionRatioBranches() throws Exception {
        TestLogger log = new TestLogger("LZ4压缩比率测试");
        log.start("测试LZ4压缩前后的压缩比率");
        
        log.step("创建LZ4CompressionStrategy");
        LZ4CompressionStrategy lz4 = new LZ4CompressionStrategy();
        
        log.step("测试初始压缩比率");
        double initialRatio = lz4.getCompressionRatio();
        log.data("初始比率", initialRatio);
        Assert.assertEquals(1.0, initialRatio, 1e-9);
        log.assertSuccess("初始比率为1.0");

        log.step("压缩数据");
        byte[] data = "hello world".getBytes("UTF-8");
        log.data("原始数据", "hello world (" + data.length + " bytes)");
        byte[] compressed = lz4.compress(data);
        log.data("压缩后大小", compressed.length + " bytes");
        Assert.assertNotNull(compressed);
        
        log.step("检查压缩后的比率");
        double ratio = lz4.getCompressionRatio();
        log.data("压缩比率", ratio);
        Assert.assertTrue(ratio > 0.0);
        log.assertSuccess("压缩比率计算正确");
        log.pass();
    }
}

