package com.brianxiadong.lsmtree;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * 一致性哈希分区策略
 * <p>
 * 使用 SHA-256 哈希值对分区数取模来决定 Key 所属的分区。
 * 注意：这里简化为模运算，标准的一致性哈希通常使用哈希环（Hash Ring）来支持动态扩缩容。
 * <p>
 * 这种策略可以均匀分布数据，但不支持高效的范围查询（必须扫描所有分区）。
 */
public class ConsistentHashPartitionStrategy implements PartitionStrategy {
    @Override
    public int getPartition(String key, int numPartitions) {
        long h = hash(key);
        // 兼容 Java 8: Math.floorMod(long, int) 是 Java 9 引入的
        // 使用 Math.floorMod(long, long)
        long idx = Math.floorMod(h, (long) numPartitions);
        return (int) idx;
    }

    @Override
    public List<Integer> getPartitionsForRange(String startKey, String endKey, int numPartitions) {
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < numPartitions; i++)
            all.add(i);
        return all; // 哈希无序，范围需遍历所有分区
    }

    private long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(key.getBytes(StandardCharsets.UTF_8));
            long v = 0;
            for (int i = 0; i < 8; i++)
                v = (v << 8) | (d[i] & 0xFF);
            return v;
        } catch (NoSuchAlgorithmException e) {
            return key.hashCode();
        }
    }
}
