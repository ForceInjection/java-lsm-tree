package com.brianxiadong.lsmtree.cache;

public interface CacheManager {
    void put(String key, Object value, CacheType type) throws CacheException;
    Object get(String key, CacheType type) throws CacheException;
    CacheStats getStats(CacheType type);
    void invalidate(String key, CacheType type) throws CacheException;
}