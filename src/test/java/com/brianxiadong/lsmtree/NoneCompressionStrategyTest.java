package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

public class NoneCompressionStrategyTest {
    @Test
    public void testPassThrough() throws Exception {
        NoneCompressionStrategy s = new NoneCompressionStrategy();
        byte[] input = "hello world".getBytes("UTF-8");
        byte[] out = s.compress(input);
        byte[] back = s.decompress(out);
        Assert.assertArrayEquals(input, back);
        Assert.assertTrue(s.getCompressionRatio() >= 1.0);
        Assert.assertEquals("NONE", s.getType());
    }
}

