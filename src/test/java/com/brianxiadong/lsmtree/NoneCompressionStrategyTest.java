package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

/**
 * 无压缩策略测试类
 * 测试NoneCompressionStrategy的透传功能
 */
public class NoneCompressionStrategyTest {
    @Test
    public void testPassThrough() throws Exception {
        TestLogger log = new TestLogger("无压缩策略测试");
        log.start("测试NoneCompressionStrategy的透传功能");
        
        log.step("创建NoneCompressionStrategy");
        NoneCompressionStrategy s = new NoneCompressionStrategy();
        log.data("压缩类型", s.getType());
        
        log.step("测试压缩和解压缩");
        byte[] input = "hello world".getBytes("UTF-8");
        log.data("原始数据", "hello world (" + input.length + " bytes)");
        
        byte[] out = s.compress(input);
        log.data("压缩后大小", out.length + " bytes");
        
        byte[] back = s.decompress(out);
        log.data("解压缩后大小", back.length + " bytes");
        
        Assert.assertArrayEquals(input, back);
        log.assertSuccess("压缩前后数据一致");
        
        log.step("验证压缩比率");
        log.data("压缩比率", s.getCompressionRatio());
        Assert.assertTrue(s.getCompressionRatio() >= 1.0);
        Assert.assertEquals("NONE", s.getType());
        log.assertSuccess("无压缩策略正确");
        log.pass();
    }
}

