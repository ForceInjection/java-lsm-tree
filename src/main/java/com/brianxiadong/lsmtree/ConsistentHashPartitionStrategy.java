package com.brianxiadong.lsmtree;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class ConsistentHashPartitionStrategy implements PartitionStrategy {
    @Override
    public int getPartition(String key, int numPartitions) {
        long h = hash(key);
        long idx = Math.floorMod(h, numPartitions);
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
