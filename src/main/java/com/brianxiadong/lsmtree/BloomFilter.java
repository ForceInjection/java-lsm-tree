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
        // 计算最优位数组大小
        this.size = (int) (-expectedElements * Math.log(falsePositiveProbability)
                / (Math.log(2) * Math.log(2)));
        // 计算最优哈希函数个数
        this.hashFunctions = (int) (size * Math.log(2) / expectedElements);
        this.bitSet = new BitSet(size);
    }

    /**
     * 向布隆过滤器添加元素
     */
    public void add(String key) {
        for (int i = 0; i < hashFunctions; i++) {
            int hash = hash(key, i);
            bitSet.set(Math.abs(hash % size));
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
            if (!bitSet.get(Math.abs(hash % size))) {
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
        BloomFilter filter = new BloomFilter(1000, 0.01); // 临时创建
        filter.bitSet.clear();
        BitSet restored = BitSet.valueOf(data);
        filter.bitSet.or(restored);
        return filter;
    }
}