package com.brianxiadong.lsmtree;

import java.util.BitSet;

/**
 * 布隆过滤器实现
 * 用于快速判断键是否可能存在于SSTable中
 */
public class BloomFilter {
    private final BitSet bitSet;
    private final int size;
    private final int hashFunctions;

    public BloomFilter(int expectedElements, double falsePositiveProbability) {
        if (expectedElements <= 0) {
            this.size = 1;
            this.hashFunctions = 1;
            this.bitSet = new BitSet(size);
            return;
        }
        int computedSize = (int) (-expectedElements * Math.log(falsePositiveProbability)
                / (Math.log(2) * Math.log(2)));
        this.size = Math.max(1, computedSize);
        int computedHashes = (int) (size * Math.log(2) / expectedElements);
        this.hashFunctions = Math.max(1, computedHashes);
        this.bitSet = new BitSet(size);
    }

    private BloomFilter(int size, int hashFunctions, BitSet bitSet) {
        this.size = Math.max(1, size);
        this.hashFunctions = Math.max(1, hashFunctions);
        this.bitSet = bitSet == null ? new BitSet(this.size) : bitSet;
    }

    /**
     * 向布隆过滤器添加元素
     */
    public void add(String key) {
        for (int i = 0; i < hashFunctions; i++) {
            int hash = hash(key, i);
            // 使用 & 0x7FFFFFFF 确保结果为非负数，避免 Math.abs(Integer.MIN_VALUE) 导致负数的问题
            int index = (hash & 0x7FFFFFFF) % size;
            bitSet.set(index);
        }
    }

    /**
     * 检查元素是否可能存在
     * 返回false表示绝对不存在
     * 返回true表示可能存在
     */
    public boolean mightContain(String key) {
        for (int i = 0; i < hashFunctions; i++) {
            int hash = hash(key, i);
            int index = (hash & 0x7FFFFFFF) % size;
            if (!bitSet.get(index)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 多重哈希函数实现
     * 使用Double Hashing技术避免实现多个独立的哈希函数
     */
    private int hash(String key, int i) {
        int hash1 = key.hashCode();
        int hash2 = hash1 >>> 16;
        return hash1 + i * hash2;
    }

    /**
     * 获取位数组序列化数据（用于持久化）
     */
    public byte[] toByteArray() {
        return bitSet.toByteArray();
    }

    /**
     * 从字节数组恢复布隆过滤器
     */
    public static BloomFilter fromByteArray(byte[] data, int size, int hashFunctions) {
        BitSet restored = data == null ? new BitSet(Math.max(1, size)) : BitSet.valueOf(data);
        return new BloomFilter(size, hashFunctions, restored);
    }
}
