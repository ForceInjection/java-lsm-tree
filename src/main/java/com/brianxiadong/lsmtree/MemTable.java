package com.brianxiadong.lsmtree;

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.List;
import java.util.ArrayList;

/**
 * 内存表实现
 * 使用跳表保证有序性和线程安全
 */
public class MemTable {
    private final ConcurrentSkipListMap<String, KeyValue> data;
    private final int maxSize;
    private volatile int currentSize;

    public MemTable(int maxSize) {
        this.data = new ConcurrentSkipListMap<>();
        this.maxSize = maxSize;
        this.currentSize = 0;
    }

    /**
     * 插入键值对
     */
    public void put(String key, String value) {
        KeyValue kv = new KeyValue(key, value);
        KeyValue oldValue = data.put(key, kv);
        if (oldValue == null) {
            currentSize++;
        }
    }

    /**
     * 删除键（插入删除标记）
     */
    public void delete(String key) {
        KeyValue tombstone = KeyValue.createTombstone(key);
        KeyValue oldValue = data.put(key, tombstone);
        if (oldValue == null) {
            currentSize++;
        }
    }

    /**
     * 查询键值
     */
    public String get(String key) {
        KeyValue kv = data.get(key);
        if (kv == null || kv.isDeleted()) {
            return null;
        }
        return kv.getValue();
    }

    /**
     * 检查是否需要刷盘
     */
    public boolean shouldFlush() {
        return currentSize >= maxSize;
    }

    /**
     * 获取所有键值对的有序列表
     */
    public List<KeyValue> getAllEntries() {
        return new ArrayList<>(data.values());
    }

    /**
     * 清空内存表
     */
    public void clear() {
        data.clear();
        currentSize = 0;
    }

    /**
     * 获取当前大小
     */
    public int size() {
        return currentSize;
    }

    /**
     * 检查是否为空
     */
    public boolean isEmpty() {
        return currentSize == 0;
    }
}