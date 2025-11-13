package com.brianxiadong.lsmtree;

import java.io.File;
import java.io.IOException;
import java.util.*;

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

    private List<KeyValue> mergeAndDedup(List<KeyValue> entries) {
        entries.sort(KeyValue::compareTo);
        Map<String, KeyValue> latest = new HashMap<>();
        for (KeyValue e : entries) {
            KeyValue cur = latest.get(e.getKey());
            if (cur == null || e.getTimestamp() > cur.getTimestamp()) latest.put(e.getKey(), e);
        }
        List<KeyValue> out = new ArrayList<>(latest.values());
        out.sort(Comparator.comparing(KeyValue::getKey));
        return out;
    }

    private List<SSTable> compactTier(List<SSTable> tables) throws IOException {
        List<KeyValue> all = new ArrayList<>();
        for (SSTable t : tables) all.addAll(t.getAllEntries());
        List<KeyValue> merged = mergeAndDedup(all);
        List<SSTable> res = new ArrayList<>();
        int entriesPer = 10000;
        for (int i = 0; i < merged.size(); i += entriesPer) {
            int end = Math.min(i + entriesPer, merged.size());
            List<KeyValue> part = merged.subList(i, end);
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
