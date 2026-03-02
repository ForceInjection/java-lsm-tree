package com.brianxiadong.lsmtree.cache;

/**
 * 内部缓存接口
 * <p>
 * 定义具体的缓存实现（如 LRU, LFU）需要具备的基本行为。
 * 泛型 K 代表键类型，V 代表值类型。
 */
public interface InternalCache<K,V> {
    /**
     * 获取缓存值
     *
     * @param key 键
     * @return 值，不存在返回 null
     */
    V get(K key);

    /**
     * 放入缓存
     *
     * @param key   键
     * @param value 值
     */
    void put(K key, V value);

    /**
     * 使缓存失效
     *
     * @param key 键
     */
    void invalidate(K key);

    /**
     * 调整容量
     *
     * @param capacity 新容量
     */
    void setCapacity(int capacity);

    /**
     * 获取当前容量
     *
     * @return 容量
     */
    int getCapacity();

    /**
     * 获取当前大小
     *
     * @return 大小
     */
    int size();

    /**
     * 获取统计信息
     *
     * @return 统计对象
     */
    CacheStats stats();
}