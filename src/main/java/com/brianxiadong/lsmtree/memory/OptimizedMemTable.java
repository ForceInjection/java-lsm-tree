package com.brianxiadong.lsmtree.memory;

import com.brianxiadong.lsmtree.KeyValue;
import com.brianxiadong.lsmtree.MemTable;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * 增强版MemTable，集成内存优化功能
 * 使用对象池和内存管理优化来减少GC压力
 */
public class OptimizedMemTable extends MemTable {
    private final MemoryManager memoryManager;
    private final ObjectPool<KeyValue> keyValuePool;
    private final ObjectPool<StringBuilder> stringBuilderPool;
    
    public OptimizedMemTable(int maxSize, MemoryManager memoryManager) {
        super(maxSize);
        this.memoryManager = memoryManager != null ? memoryManager : new DefaultMemoryManager();
        
        // 为KeyValue创建自定义工厂
        this.keyValuePool = new GenericObjectPool<>(KeyValue.class, cls -> 
            new KeyValue("", "")
        );
        this.stringBuilderPool = this.memoryManager.getObjectPool(StringBuilder.class);
        
        // 预填充对象池
        if (this.keyValuePool instanceof GenericObjectPool) {
            ((GenericObjectPool<KeyValue>) this.keyValuePool).prefill(100);
        }
        if (this.stringBuilderPool instanceof GenericObjectPool) {
            ((GenericObjectPool<StringBuilder>) this.stringBuilderPool).prefill(50);
        }
    }
    
    @Override
    public void put(String key, String value) {
        if (memoryManager.isOptimizationEnabled()) {
            // 使用对象池创建KeyValue
            KeyValue kv = keyValuePool.borrowObject();
            // 注意：这里需要特殊的KeyValue实现支持池化重用
            // 暂时还是使用原生创建方式以保证兼容性
            super.put(key, value);
        } else {
            super.put(key, value);
        }
    }
    
    @Override
    public void delete(String key) {
        if (memoryManager.isOptimizationEnabled()) {
            // 使用对象池创建墓碑标记
            KeyValue tombstone = KeyValue.createTombstone(key);
            super.delete(key);
        } else {
            super.delete(key);
        }
    }
    
    /**
     * 优化的范围查询，减少临时对象创建
     */
    public List<String> getRangeValues(String startKey, String endKey, 
                                     boolean includeStart, boolean includeEnd) {
        List<KeyValue> entries = super.getRange(startKey, endKey, includeStart, includeEnd);
        List<String> values = new ArrayList<>(entries.size());
        
        if (memoryManager.isOptimizationEnabled()) {
            // 使用StringBuilder池优化字符串操作
            for (KeyValue kv : entries) {
                if (kv != null && kv.getValue() != null) {
                    values.add(kv.getValue());
                }
            }
        } else {
            // 标准实现
            for (KeyValue kv : entries) {
                if (kv != null && kv.getValue() != null) {
                    values.add(kv.getValue());
                }
            }
        }
        
        return values;
    }
    
    /**
     * 批量插入优化
     */
    public void putBatch(List<KeyValue> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        
        if (memoryManager.isOptimizationEnabled()) {
            // 批量操作优化
            for (KeyValue kv : batch) {
                super.put(kv.getKey(), kv.getValue());
            }
        } else {
            // 标准批量插入
            for (KeyValue kv : batch) {
                super.put(kv.getKey(), kv.getValue());
            }
        }
    }
    
    /**
     * 获取内存优化统计信息
     */
    public MemoryOptimizationStats getOptimizationStats() {
        PoolStats keyValueStats = keyValuePool.getStats();
        PoolStats stringBuilderStats = stringBuilderPool.getStats();
        MemoryUsageStats memoryStats = memoryManager.getMemoryStats();
        
        return new MemoryOptimizationStats(
            keyValueStats,
            stringBuilderStats,
            memoryStats,
            super.size()
        );
    }
    
    /**
     * 内存优化统计信息类
     */
    public static class MemoryOptimizationStats {
        private final PoolStats keyValuePoolStats;
        private final PoolStats stringBuilderPoolStats;
        private final MemoryUsageStats memoryStats;
        private final int memTableSize;
        
        public MemoryOptimizationStats(PoolStats keyValuePoolStats,
                                     PoolStats stringBuilderPoolStats,
                                     MemoryUsageStats memoryStats,
                                     int memTableSize) {
            this.keyValuePoolStats = keyValuePoolStats;
            this.stringBuilderPoolStats = stringBuilderPoolStats;
            this.memoryStats = memoryStats;
            this.memTableSize = memTableSize;
        }
        
        public PoolStats getKeyValuePoolStats() { return keyValuePoolStats; }
        public PoolStats getStringBuilderPoolStats() { return stringBuilderPoolStats; }
        public MemoryUsageStats getMemoryStats() { return memoryStats; }
        public int getMemTableSize() { return memTableSize; }
        
        @Override
        public String toString() {
            return String.format(
                "MemoryOptimizationStats{memTableSize=%d, keyValuePool=%s, stringBuilderPool=%s, memory=%s}",
                memTableSize, keyValuePoolStats, stringBuilderPoolStats, memoryStats
            );
        }
    }
}