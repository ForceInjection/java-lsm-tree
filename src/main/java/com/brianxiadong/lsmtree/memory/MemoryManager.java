package com.brianxiadong.lsmtree.memory;

import java.nio.ByteBuffer;

/**
 * 内存管理器接口
 * 提供统一的内存管理、对象池、堆外内存和GC优化功能
 */
public interface MemoryManager {
    
    /**
     * 分配ByteBuffer
     * @param size 缓冲区大小
     * @param direct 是否使用堆外内存
     * @return 分配的ByteBuffer
     */
    ByteBuffer allocate(int size, boolean direct);
    
    /**
     * 释放ByteBuffer
     * @param buffer 要释放的缓冲区
     */
    void deallocate(ByteBuffer buffer);
    
    /**
     * 获取指定类型的对象池
     * @param clazz 对象类型
     * @param <T> 泛型类型
     * @return 对象池实例
     */
    <T> ObjectPool<T> getObjectPool(Class<T> clazz);
    
    /**
     * 归还对象到对应的对象池
     * @param obj 要归还的对象
     */
    void returnObject(Object obj);
    
    /**
     * 获取内存使用统计信息
     * @return 内存使用统计
     */
    MemoryUsageStats getMemoryStats();
    
    /**
     * 主动触发垃圾回收
     */
    void triggerGC();
    
    /**
     * 更新GC配置
     * @param config GC配置
     */
    void updateGCConfig(GCConfig config);
    
    /**
     * 启用内存优化功能
     */
    void enableOptimization();
    
    /**
     * 禁用内存优化功能
     */
    void disableOptimization();
    
    /**
     * 检查是否启用了内存优化
     * @return 是否启用优化
     */
    boolean isOptimizationEnabled();
}