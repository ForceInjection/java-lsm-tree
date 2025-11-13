package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

public class BloomFilterPersistenceTest {
    @Test
    public void testToFromByteArray() {
        BloomFilter bf = new BloomFilter(100, 0.01);
        bf.add("a");
        bf.add("b");
        byte[] bytes = bf.toByteArray();
        BloomFilter restored = BloomFilter.fromByteArray(bytes, 100, 7);
        Assert.assertTrue(restored.mightContain("a"));
        Assert.assertTrue(restored.mightContain("b"));
        Assert.assertFalse(restored.mightContain("not-exist"));
    }
}

