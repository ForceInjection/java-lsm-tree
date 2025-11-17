package com.brianxiadong.lsmtree.cache;

import com.brianxiadong.lsmtree.KeyValue;
import com.brianxiadong.lsmtree.MetricsRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class CacheManagerImpl implements CacheManager {
    private final InternalCache<String, KeyValue> rowCache;
    private final InternalCache<String, Block> blockCache;

    public CacheManagerImpl(int rowCapacity, int blockCapacity, CacheStrategy strategy) {
        this.rowCache = strategy == CacheStrategy.LFU ? new LFUCache<>(rowCapacity) : new LRUCache<>(rowCapacity);
        this.blockCache = strategy == CacheStrategy.LFU ? new LFUCache<>(blockCapacity) : new LRUCache<>(blockCapacity);
        MeterRegistry registry = MetricsRegistry.get();
        Gauge.builder("lsm.cache.row.hits", rowCache.stats(), s -> (double) s.getHits()).register(registry);
        Gauge.builder("lsm.cache.row.misses", rowCache.stats(), s -> (double) s.getMisses()).register(registry);
        Gauge.builder("lsm.cache.row.evictions", rowCache.stats(), s -> (double) s.getEvictions()).register(registry);
        Gauge.builder("lsm.cache.row.size", rowCache.stats(), s -> (double) s.getSize()).register(registry);
        Gauge.builder("lsm.cache.row.capacity", rowCache.stats(), s -> (double) s.getCapacity()).register(registry);
        Gauge.builder("lsm.cache.row.hitRatio", rowCache.stats(), s -> s.getHitRatio()).register(registry);
        Gauge.builder("lsm.cache.block.hits", blockCache.stats(), s -> (double) s.getHits()).register(registry);
        Gauge.builder("lsm.cache.block.misses", blockCache.stats(), s -> (double) s.getMisses()).register(registry);
        Gauge.builder("lsm.cache.block.evictions", blockCache.stats(), s -> (double) s.getEvictions()).register(registry);
        Gauge.builder("lsm.cache.block.size", blockCache.stats(), s -> (double) s.getSize()).register(registry);
        Gauge.builder("lsm.cache.block.capacity", blockCache.stats(), s -> (double) s.getCapacity()).register(registry);
        Gauge.builder("lsm.cache.block.hitRatio", blockCache.stats(), s -> s.getHitRatio()).register(registry);
    }

    public void setRowCacheTTLMillis(long ttlMillis) {
        if (rowCache instanceof LRUCache) ((LRUCache<String, KeyValue>) rowCache).setDefaultTTLMillis(ttlMillis);
        if (rowCache instanceof LFUCache) ((LFUCache<String, KeyValue>) rowCache).setDefaultTTLMillis(ttlMillis);
    }

    public void setBlockCacheTTLMillis(long ttlMillis) {
        if (blockCache instanceof LRUCache) ((LRUCache<String, Block>) blockCache).setDefaultTTLMillis(ttlMillis);
        if (blockCache instanceof LFUCache) ((LFUCache<String, Block>) blockCache).setDefaultTTLMillis(ttlMillis);
    }

    public void adjustCapacity(CacheType type, int capacity) {
        if (type == CacheType.ROW) rowCache.setCapacity(capacity);
        else blockCache.setCapacity(capacity);
    }

    public CacheStats getCombinedStats() {
        CacheStats s = new CacheStats();
        s.setCapacity(rowCache.getCapacity() + blockCache.getCapacity());
        s.setSize(rowCache.size() + blockCache.size());
        return s;
    }

    @Override
    public void put(String key, Object value, CacheType type) throws CacheException {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        if (type == CacheType.ROW) {
            if (!(value instanceof KeyValue)) throw new CacheException("ROW cache requires KeyValue");
            rowCache.put(key, (KeyValue) value);
        } else {
            if (!(value instanceof Block)) throw new CacheException("BLOCK cache requires Block");
            blockCache.put(key, (Block) value);
        }
    }

    @Override
    public Object get(String key, CacheType type) throws CacheException {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        if (type == CacheType.ROW) {
            return rowCache.get(key);
        } else {
            return blockCache.get(key);
        }
    }

    @Override
    public CacheStats getStats(CacheType type) {
        return type == CacheType.ROW ? rowCache.stats() : blockCache.stats();
    }

    @Override
    public void invalidate(String key, CacheType type) throws CacheException {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        if (type == CacheType.ROW) rowCache.invalidate(key); else blockCache.invalidate(key);
    }

    public String computeBlockId(String key) {
        int h = key == null ? 0 : key.hashCode();
        int bucket = (h >>> 20) & 0xFFF; // 4096 buckets
        return Integer.toString(bucket);
    }

    public void populateBlockForKeys(Iterable<KeyValue> entries) {
        Map<String, Map<String, KeyValue>> buckets = new HashMap<>();
        for (KeyValue kv : entries) {
            String bid = computeBlockId(kv.getKey());
            buckets.computeIfAbsent(bid, x -> new HashMap<>()).put(kv.getKey(), kv);
        }
        for (Map.Entry<String, Map<String, KeyValue>> e : buckets.entrySet()) {
            Block b = new Block(e.getKey(), e.getValue());
            blockCache.put(e.getKey(), b);
        }
    }
}