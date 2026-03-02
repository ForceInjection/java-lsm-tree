package com.brianxiadong.lsmtree;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Size-Tiered 压缩策略
 * <p>
 * 类似于 Cassandra 的 SizeTieredCompactionStrategy。
 * 将 SSTable 按照大小分层 (Tier)，当某个层的 SSTable 数量达到阈值 (minFilesPerTier) 时，
 * 将它们合并为一个更大的 SSTable。
 * <p>
 * 适用场景：写密集型负载 (Write-Heavy Workloads)。
 * 优点：写入放大 (Write Amplification) 较低。
 * 缺点：读取放大 (Read Amplification) 较高，空间放大 (Space Amplification) 较高（可能需要 50% 的额外空间用于合并）。
 */
public class SizeTieredCompactionStrategy implements CompactionStrategy {
    private final String dataDir;
    private final long baseSizeBytes;
    private final int minFilesPerTier;
    private CompressionStrategy compressionStrategy = new NoneCompressionStrategy();

    public SizeTieredCompactionStrategy(String dataDir, long baseSizeBytes, int minFilesPerTier) {
        this.dataDir = dataDir;
        this.baseSizeBytes = baseSizeBytes;
        this.minFilesPerTier = minFilesPerTier;
    }

    @Override
    public boolean needsCompaction(List<SSTable> ssTables) {
        Map<Integer, List<SSTable>> tiers = groupByTier(ssTables);
        for (List<SSTable> v : tiers.values()) {
            if (v.size() >= minFilesPerTier) return true;
        }
        return false;
    }

    @Override
    public List<SSTable> compact(List<SSTable> ssTables) throws IOException {
        Map<Integer, List<SSTable>> tiers = groupByTier(ssTables);
        List<SSTable> out = new ArrayList<>();
        for (Map.Entry<Integer, List<SSTable>> e : tiers.entrySet()) {
            List<SSTable> list = e.getValue();
            if (list.size() >= minFilesPerTier) {
                out.addAll(compactTier(list));
                for (SSTable t : list) new File(t.getFilePath()).delete();
            } else {
                out.addAll(list);
            }
        }
        return out;
    }

    @Override
    public LeveledCompactionStrategy.CompactionTask selectCompactionTask(List<SSTable> ssTables) {
        Map<Integer, List<SSTable>> tiers = groupByTier(ssTables);
        int bestTier = -1;
        int bestCount = 0;
        for (Map.Entry<Integer, List<SSTable>> e : tiers.entrySet()) {
            if (e.getValue().size() > bestCount && e.getValue().size() >= minFilesPerTier) {
                bestCount = e.getValue().size();
                bestTier = e.getKey();
            }
        }
        if (bestTier == -1) return null;
        return new LeveledCompactionStrategy.CompactionTask(bestTier, tiers.get(bestTier));
    }

    private Map<Integer, List<SSTable>> groupByTier(List<SSTable> ssTables) {
        Map<Integer, List<SSTable>> tiers = new HashMap<>();
        for (SSTable t : ssTables) {
            long size = new File(t.getFilePath()).length();
            int tier = calcTier(size);
            tiers.computeIfAbsent(tier, k -> new ArrayList<>()).add(t);
        }
        return tiers;
    }

    private int calcTier(long size) {
        if (size <= 0 || baseSizeBytes <= 0) return 0;
        int tier = 0;
        long s = baseSizeBytes;
        while (size > s) {
            s <<= 1;
            tier++;
        }
        return tier;
    }

    /**
     * 合并数据并去重
     * 保留相同 Key 的最新版本（Timestamp 最大）
     *
     * @param entries 原始键值对列表
     * @return 去重并排序后的键值对列表
     */
    private List<KeyValue> mergeAndDedup(List<KeyValue> entries) {
        // 1. 初次排序，确保相同 Key 的数据聚集在一起
        entries.sort(KeyValue::compareTo);
        
        // 2. 使用 Map 去重，保留最新版本
        // 注意：这里使用 HashMap 会打乱顺序，导致后面需要再次排序
        Map<String, KeyValue> latest = new HashMap<>();
        for (KeyValue e : entries) {
            KeyValue cur = latest.get(e.getKey());
            // 如果当前 Map 中没有该 Key，或者新条目的时间戳更大（或相等），则更新
            if (cur == null || e.getTimestamp() >= cur.getTimestamp()) {
                latest.put(e.getKey(), e);
            }
        }
        
        // 3. 再次排序输出
        List<KeyValue> out = new ArrayList<>(latest.values());
        out.sort(Comparator.comparing(KeyValue::getKey));
        return out;
    }

    /**
     * 将多个 SSTable 合并为一个或多个新的 SSTable
     * <p>
     * 注意：当前实现会将所有待合并的数据加载到内存中，这在数据量较大时会导致 OOM。
     * 生产环境应当使用流式合并（Merge Sort Iterator）来避免将所有数据一次性加载到内存。
     *
     * @param tables 待合并的 SSTable 列表
     * @return 合并后的新 SSTable 列表
     * @throws IOException 如果发生 IO 错误
     */
    private List<SSTable> compactTier(List<SSTable> tables) throws IOException {
        List<KeyValue> all = new ArrayList<>();
        // 警告：这里将所有数据加载到内存，存在 OOM 风险
        for (SSTable t : tables) all.addAll(t.getAllEntries());
        
        List<KeyValue> merged = mergeAndDedup(all);
        List<SSTable> res = new ArrayList<>();
        int entriesPer = 10000; // 每个新 SSTable 的最大条目数
        
        for (int i = 0; i < merged.size(); i += entriesPer) {
            int end = Math.min(i + entriesPer, merged.size());
            List<KeyValue> part = merged.subList(i, end);
            // 生成新的文件名，使用时间戳和索引防止冲突
            String file = String.format("%s/sstable_level1_%d_%d.db", dataDir, System.currentTimeMillis(), i);
            res.add(new SSTable(file, part, compressionStrategy));
        }
        return res;
    }

    @Override
    public void setCompressionStrategy(CompressionStrategy compressionStrategy) {
        this.compressionStrategy = compressionStrategy == null ? new NoneCompressionStrategy() : compressionStrategy;
    }
}
