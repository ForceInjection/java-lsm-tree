package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

public class LZ4CompressionStrategyBranchTest {
    @Test
    public void testCompressionRatioBranches() throws Exception {
        LZ4CompressionStrategy lz4 = new LZ4CompressionStrategy();
        // 分支1：尚未压缩任何数据时，ratio 应为 1.0
        Assert.assertEquals(1.0, lz4.getCompressionRatio(), 1e-9);

        // 分支2：压缩后，ratio 基于 out/in 计算
        byte[] data = "hello world".getBytes("UTF-8");
        byte[] compressed = lz4.compress(data);
        Assert.assertNotNull(compressed);
        double ratio = lz4.getCompressionRatio();
        Assert.assertTrue(ratio > 0.0);
    }
}

