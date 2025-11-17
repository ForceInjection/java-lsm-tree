package com.brianxiadong.lsmtree.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class LRUCache<K,V> implements InternalCache<K,V> {
    private final ReentrantLock lock = new ReentrantLock();
    private volatile int capacity;
    private final CacheStats stats = new CacheStats();
    private final LinkedHashMap<K, Entry<V>> map;
    private volatile long ttlMillis = 0L;

    private static class Entry<V> {
        final V value;
        final long ts;
        Entry(V v, long ts) { this.value = v; this.ts = ts; }
    }

    public LRUCache(int capacity) {
        this.capacity = Math.max(1, capacity);
        this.map = new LinkedHashMap<K, Entry<V>>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, Entry<V>> eldest) {
                boolean evict = size() > LRUCache.this.capacity;
                if (evict) stats.recordEviction();
                return evict;
            }
        };
        stats.setCapacity(this.capacity);
    }

    public void setDefaultTTLMillis(long ttlMillis) {
        this.ttlMillis = Math.max(0L, ttlMillis);
    }

    @Override
    public V get(K key) {
        lock.lock();
        try {
            Entry<V> e = map.get(key);
            if (e == null) { stats.recordMiss(); return null; }
            if (ttlMillis > 0 && (System.currentTimeMillis() - e.ts) > ttlMillis) {
                map.remove(key);
                stats.recordMiss();
                return null;
            }
            stats.recordHit();
            return e.value;
        } finally {
            stats.setSize(map.size());
            lock.unlock();
        }
    }

    @Override
    public void put(K key, V value) {
        lock.lock();
        try {
            map.put(key, new Entry<>(value, System.currentTimeMillis()));
        } finally {
            stats.setSize(map.size());
            lock.unlock();
        }
    }

    @Override
    public void invalidate(K key) {
        lock.lock();
        try {
            map.remove(key);
        } finally {
            stats.setSize(map.size());
            lock.unlock();
        }
    }

    @Override
    public void setCapacity(int capacity) {
        lock.lock();
        try {
            this.capacity = Math.max(1, capacity);
            stats.setCapacity(this.capacity);
            while (map.size() > this.capacity) {
                Map.Entry<K, Entry<V>> first = map.entrySet().iterator().next();
                map.remove(first.getKey());
                stats.recordEviction();
            }
        } finally {
            stats.setSize(map.size());
            lock.unlock();
        }
    }

    @Override
    public int getCapacity() { return capacity; }
    @Override
    public int size() { return map.size(); }
    @Override
    public CacheStats stats() { return stats; }
}