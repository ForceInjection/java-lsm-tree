package com.brianxiadong.lsmtree.cache;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * LFU (Least Frequently Used) 缓存实现
 * <p>
 * 根据元素的使用频率进行驱逐，频率最低的元素最先被移除。
 * 如果频率相同，则移除最旧的（LRU 规则）。
 * 实现了 O(1) 的 put 和 get 操作。
 * 线程安全。
 */
public class LFUCache<K,V> implements InternalCache<K,V> {
    private final ReentrantLock lock = new ReentrantLock();
    private volatile int capacity;
    private final CacheStats stats = new CacheStats();

    private static class Node<V> {
        V val; int freq; long ts;
        Node(V v) { this.val = v; this.freq = 1; this.ts = System.currentTimeMillis(); }
    }

    private final Map<K, Node<V>> store = new HashMap<>();
    private final Map<Integer, LinkedHashSet<K>> freqBuckets = new HashMap<>();
    private int minFreq = 1;
    private volatile long ttlMillis = 0L;

    public LFUCache(int capacity) {
        this.capacity = Math.max(1, capacity);
        stats.setCapacity(this.capacity);
    }

    public void setDefaultTTLMillis(long ttlMillis) { this.ttlMillis = Math.max(0L, ttlMillis); }

    @Override
    public V get(K key) {
        lock.lock();
        try {
            Node<V> n = store.get(key);
            if (n == null) { stats.recordMiss(); return null; }
            if (ttlMillis > 0 && (System.currentTimeMillis() - n.ts) > ttlMillis) {
                removeKey(key, n.freq);
                stats.recordMiss();
                return null;
            }
            touch(key, n);
            stats.recordHit();
            return n.val;
        } finally {
            stats.setSize(store.size());
            lock.unlock();
        }
    }

    @Override
    public void put(K key, V value) {
        lock.lock();
        try {
            if (store.containsKey(key)) {
                Node<V> n = store.get(key);
                n.val = value; n.ts = System.currentTimeMillis();
                touch(key, n);
            } else {
                if (store.size() >= capacity) evictOne();
                Node<V> n = new Node<>(value);
                store.put(key, n);
                freqBuckets.computeIfAbsent(1, f -> new LinkedHashSet<>()).add(key);
                minFreq = 1;
            }
        } finally {
            stats.setSize(store.size());
            lock.unlock();
        }
    }

    @Override
    public void invalidate(K key) {
        lock.lock();
        try {
            Node<V> n = store.get(key);
            if (n != null) removeKey(key, n.freq);
        } finally {
            stats.setSize(store.size());
            lock.unlock();
        }
    }

    private void touch(K key, Node<V> n) {
        LinkedHashSet<K> set = freqBuckets.get(n.freq);
        if (set != null) set.remove(key);
        n.freq++;
        freqBuckets.computeIfAbsent(n.freq, f -> new LinkedHashSet<>()).add(key);
        if (set != null && set.isEmpty() && minFreq == n.freq - 1) minFreq++;
    }

    private void evictOne() {
        LinkedHashSet<K> set = freqBuckets.get(minFreq);
        if (set == null || set.isEmpty()) {
            Optional<Integer> next = freqBuckets.keySet().stream().min(Integer::compareTo);
            if (next.isPresent()) set = freqBuckets.get(next.get());
        }
        if (set != null && !set.isEmpty()) {
            Iterator<K> it = set.iterator();
            K k = it.next(); it.remove();
            store.remove(k);
            stats.recordEviction();
        }
    }

    private void removeKey(K key, int freq) {
        store.remove(key);
        LinkedHashSet<K> set = freqBuckets.get(freq);
        if (set != null) set.remove(key);
    }

    @Override
    public void setCapacity(int capacity) {
        lock.lock();
        try {
            this.capacity = Math.max(1, capacity);
            stats.setCapacity(this.capacity);
            while (store.size() > this.capacity) evictOne();
        } finally {
            stats.setSize(store.size());
            lock.unlock();
        }
    }

    @Override
    public int getCapacity() { return capacity; }
    @Override
    public int size() { return store.size(); }
    @Override
    public CacheStats stats() { return stats; }
}