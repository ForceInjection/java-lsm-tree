package com.brianxiadong.lsmtree.cache;

/**
 * 缓存管理器接口
 * <p>
 * 统一管理行缓存 (Row Cache) 和块缓存 (Block Cache)。
 * 提供缓存的增删改查、统计信息获取和容量调整等功能。
 */
public interface CacheManager {
    /**
     * 将数据放入缓存
     *
     * @param key   缓存键
     * @param value 缓存值（KeyValue 或 Block）
     * @param type  缓存类型
     * @throws CacheException 如果参数错误或类型不匹配
     */
    void put(String key, Object value, CacheType type) throws CacheException;

    /**
     * 从缓存获取数据
     *
     * @param key  缓存键
     * @param type 缓存类型
     * @return 缓存值，如果不存在则返回 null
     * @throws CacheException 如果参数错误
     */
    Object get(String key, CacheType type) throws CacheException;

    /**
     * 获取指定类型缓存的统计信息
     *
     * @param type 缓存类型
     * @return 统计信息
     */
    CacheStats getStats(CacheType type);

    /**
     * 使缓存失效
     *
     * @param key  缓存键
     * @param type 缓存类型
     * @throws CacheException 如果参数错误
     */
    void invalidate(String key, CacheType type) throws CacheException;
}