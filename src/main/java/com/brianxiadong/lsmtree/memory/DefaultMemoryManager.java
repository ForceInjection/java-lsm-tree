package com.brianxiadong.lsmtree.memory;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;

/**
 * 内存管理器默认实现
 * <p>
 * 整合了 {@link DirectMemoryManager} 和 {@link GenericObjectPool}，
 * 提供基于开关的内存优化策略。
 * 线程安全。
 */
public class DefaultMemoryManager implements MemoryManager {
    
    private final AtomicBoolean optimizationEnabled = new AtomicBoolean(false);
    private final Map<Class<?>, ObjectPool<?>> objectPools = new ConcurrentHashMap<>();
    private final DirectMemoryManager directMemoryManager;
    private final MemoryMonitor memoryMonitor;
    private GCConfig currentGCConfig;
    
    public DefaultMemoryManager() {
        this.directMemoryManager = new DirectMemoryManager();
        this.memoryMonitor = new MemoryMonitor();
        this.currentGCConfig = new GCConfig();
    }
    
    @Override
    public ByteBuffer allocate(int size, boolean direct) {
        if (!optimizationEnabled.get()) {
            return direct ? ByteBuffer.allocateDirect(size) : ByteBuffer.allocate(size);
        }
        
        if (direct) {
            return directMemoryManager.allocate(size);
        } else {
            // 对于堆内内存，也可以通过池化优化
            return ByteBuffer.allocate(size);
        }
    }
    
    @Override
    public void deallocate(ByteBuffer buffer) {
        if (!optimizationEnabled.get()) {
            return; // 直接让GC处理
        }
        
        if (buffer.isDirect()) {
            directMemoryManager.deallocate(buffer);
        }
        // 堆内ByteBuffer由GC自动回收
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <T> ObjectPool<T> getObjectPool(Class<T> clazz) {
        return (ObjectPool<T>) objectPools.computeIfAbsent(clazz, 
            k -> new GenericObjectPool<>(clazz));
    }
    
    @Override
    public void returnObject(Object obj) {
        if (!optimizationEnabled.get() || obj == null) {
            return;
        }
        
        Class<?> clazz = obj.getClass();
        ObjectPool<?> pool = objectPools.get(clazz);
        if (pool != null) {
            try {
                @SuppressWarnings("unchecked")
                ObjectPool<Object> objPool = (ObjectPool<Object>) pool;
                objPool.returnObject(obj);
            } catch (Exception e) {
                // 如果归还不成功，就让GC处理
            }
        }
    }
    
    @Override
    public MemoryUsageStats getMemoryStats() {
        return memoryMonitor.getMemoryStats();
    }
    
    @Override
    public void triggerGC() {
        System.gc();
        memoryMonitor.recordGCCycle();
    }
    
    @Override
    public void updateGCConfig(GCConfig config) {
        this.currentGCConfig = config;
        // 实际应用中这里应该动态调整JVM参数
        System.out.println("GC配置已更新: " + config);
    }
    
    @Override
    public void enableOptimization() {
        if (optimizationEnabled.compareAndSet(false, true)) {
            System.out.println("内存优化已启用");
            memoryMonitor.startMonitoring();
        }
    }
    
    @Override
    public void disableOptimization() {
        if (optimizationEnabled.compareAndSet(true, false)) {
            System.out.println("内存优化已禁用");
            memoryMonitor.stopMonitoring();
            // 清空所有对象池
            objectPools.values().forEach(ObjectPool::clear);
        }
    }
    
    @Override
    public boolean isOptimizationEnabled() {
        return optimizationEnabled.get();
    }
    
    /**
     * 获取当前GC配置
     */
    public GCConfig getCurrentGCConfig() {
        return currentGCConfig;
    }
    
    /**
     * 获取对象池统计信息
     */
    public Map<Class<?>, PoolStats> getPoolStats() {
        Map<Class<?>, PoolStats> stats = new ConcurrentHashMap<>();
        objectPools.forEach((clazz, pool) -> stats.put(clazz, pool.getStats()));
        return stats;
    }
    
    /**
     * 清理资源
     */
    public void shutdown() {
        disableOptimization();
        directMemoryManager.shutdown();
        memoryMonitor.shutdown();
    }
}