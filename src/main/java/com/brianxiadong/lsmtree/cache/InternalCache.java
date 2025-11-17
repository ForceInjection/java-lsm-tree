package com.brianxiadong.lsmtree.cache;

public interface InternalCache<K,V> {
    V get(K key);
    void put(K key, V value);
    void invalidate(K key);
    void setCapacity(int capacity);
    int getCapacity();
    int size();
    CacheStats stats();
}