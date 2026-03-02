package com.brianxiadong.lsmtree.memory;

import java.nio.ByteBuffer;

/**
 * 内存管理器接口
 * <p>
 * 提供统一的内存资源管理功能，包括：
 * <ul>
 *   <li>ByteBuffer 的分配与释放（支持堆内和堆外内存）</li>
 *   <li>对象池管理（减少对象创建开销）</li>
 *   <li>GC 监控与优化配置</li>
 *   <li>内存使用统计</li>
 * </ul>
 */
public interface MemoryManager {
    
    /**
     * 分配 ByteBuffer
     * 
     * @param size 缓冲区大小（字节）
     * @param direct 是否使用堆外内存 (Direct Buffer)
     * @return 分配的 ByteBuffer
     */
    ByteBuffer allocate(int size, boolean direct);
    
    /**
     * 释放 ByteBuffer
     * <p>
     * 对于堆外内存，尝试回收到池中或释放；
     * 对于堆内内存，通常由 GC 自动处理，但此方法可用于统计或特定优化。
     * 
     * @param buffer 要释放的缓冲区
     */
    void deallocate(ByteBuffer buffer);
    
    /**
     * 获取指定类型的对象池
     * 
     * @param clazz 对象类型
     * @param <T> 泛型类型
     * @return 对象池实例
     */
    <T> ObjectPool<T> getObjectPool(Class<T> clazz);
    
    /**
     * 归还对象到对应的对象池
     * 
     * @param obj 要归还的对象
     */
    void returnObject(Object obj);
    
    /**
     * 获取内存使用统计信息
     * 
     * @return 内存使用统计对象
     */
    MemoryUsageStats getMemoryStats();
    
    /**
     * 主动触发垃圾回收 (System.gc())
     * <p>
     * 注意：这只是建议 JVM 进行 GC，并不保证立即执行。
     * 频繁调用可能会影响性能。
     */
    void triggerGC();
    
    /**
     * 更新 GC 配置
     * 
     * @param config 新的 GC 配置
     */
    void updateGCConfig(GCConfig config);
    
    /**
     * 启用内存优化功能
     * <p>
     * 开启对象池、堆外内存池等优化机制。
     */
    void enableOptimization();
    
    /**
     * 禁用内存优化功能
     * <p>
     * 关闭优化机制并清理相关资源。
     */
    void disableOptimization();
    
    /**
     * 检查是否启用了内存优化
     * 
     * @return 如果启用了优化则返回 true，否则返回 false
     */
    boolean isOptimizationEnabled();
}