package com.brianxiadong.lsmtree.memory;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 通用对象池实现
 * @param <T> 对象类型
 */
public class GenericObjectPool<T> implements ObjectPool<T> {
    
    private final ConcurrentLinkedQueue<T> pool = new ConcurrentLinkedQueue<>();
    private final Supplier<T> factory;
    private final Class<T> clazz;
    
    // 统计信息
    private final AtomicInteger activeCount = new AtomicInteger(0);
    private final AtomicInteger idleCount = new AtomicInteger(0);
    private final AtomicLong borrowedCount = new AtomicLong(0);
    private final AtomicLong returnedCount = new AtomicLong(0);
    private final AtomicLong createdCount = new AtomicLong(0);
    private final AtomicLong destroyedCount = new AtomicLong(0);
    
    // 配置参数
    private final int maxSize;
    private final int minIdle;
    private final int maxIdle;
    private final long validationIntervalMs;
    
    // 回收策略
    private final boolean enableEviction;
    private final long evictionRunIntervalMs;
    private final java.util.concurrent.ScheduledExecutorService evictionExecutor;
    
    public GenericObjectPool(Class<T> clazz) {
        this.clazz = clazz;
        this.factory = this::createDefaultInstance;
        this.maxSize = 1000;
        this.minIdle = 5;  // 减少最小空闲对象
        this.maxIdle = 50; // 增加最大空闲对象
        this.validationIntervalMs = 30000; // 30秒验证间隔
        this.enableEviction = true;
        this.evictionRunIntervalMs = 60000; // 1分钟运行一次回收
        this.evictionExecutor = createEvictionExecutor();
        startEvictionTask();
    }
    
    public GenericObjectPool(Class<T> clazz, java.util.function.Function<Class<T>, T> customFactory) {
        this.clazz = clazz;
        this.factory = () -> customFactory.apply(clazz);
        this.maxSize = 1000;
        this.minIdle = 5;
        this.maxIdle = 50;
        this.validationIntervalMs = 30000;
        this.enableEviction = true;
        this.evictionRunIntervalMs = 60000;
        this.evictionExecutor = createEvictionExecutor();
        startEvictionTask();
    }
    
    public GenericObjectPool(Class<T> clazz, Supplier<T> factory) {
        this.clazz = clazz;
        this.factory = factory;
        this.maxSize = 1000;
        this.minIdle = 5;
        this.maxIdle = 50;
        this.validationIntervalMs = 30000;
        this.enableEviction = true;
        this.evictionRunIntervalMs = 60000;
        this.evictionExecutor = createEvictionExecutor();
        startEvictionTask();
    }
    
    @Override
    public T borrowObject() {
        T obj = pool.poll();
        if (obj != null) {
            idleCount.decrementAndGet();
        } else {
            obj = createObject();
            createdCount.incrementAndGet();
        }
        
        activeCount.incrementAndGet();
        borrowedCount.incrementAndGet();
        return obj;
    }
    
    @Override
    public void returnObject(T obj) {
        if (obj == null) return;
        
        if (validateObject(obj) && pool.size() < maxIdle) { // 使用maxIdle而不是maxSize
            pool.offer(obj);
            idleCount.incrementAndGet();
            returnedCount.incrementAndGet();
        } else {
            destroyObject(obj);
            destroyedCount.incrementAndGet();
        }
        
        activeCount.decrementAndGet();
    }
    
    @Override
    public T createObject() {
        try {
            return factory.get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create object of type: " + clazz.getSimpleName(), e);
        }
    }
    
    @Override
    public void destroyObject(T obj) {
        // 对于大多数对象，只需要让GC处理
        // 特殊对象可以在子类中重写此方法
    }
    
    @Override
    public boolean validateObject(T obj) {
        // 默认认为所有对象都是有效的
        // 特殊验证可以在子类中重写
        return obj != null;
    }
    
    @Override
    public PoolStats getStats() {
        return new PoolStats(
            activeCount.get(),
            idleCount.get(),
            activeCount.get() + idleCount.get(),
            borrowedCount.get(),
            returnedCount.get(),
            createdCount.get(),
            destroyedCount.get()
        );
    }
    
    @Override
    public void clear() {
        T obj;
        while ((obj = pool.poll()) != null) {
            destroyObject(obj);
            destroyedCount.incrementAndGet();
            idleCount.decrementAndGet();
        }
    }
    
    /**
     * 创建默认实例的方法
     * 可以被子类重写以提供特定的创建逻辑
     */
    protected T createDefaultInstance() {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("无法创建 " + clazz.getSimpleName() + " 实例", e);
        }
    }
    
    /**
     * 预填充池
     */
    public void prefill(int count) {
        int toCreate = Math.min(count, maxIdle - pool.size()); // 使用maxIdle限制
        for (int i = 0; i < toCreate; i++) {
            T obj = createObject();
            pool.offer(obj);
            idleCount.incrementAndGet();
            createdCount.incrementAndGet();
        }
    }
    
    /**
     * 创建回收执行器
     */
    private java.util.concurrent.ScheduledExecutorService createEvictionExecutor() {
        return java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "object-pool-evictor-" + clazz.getSimpleName());
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * 启动回收任务
     */
    private void startEvictionTask() {
        if (enableEviction && evictionExecutor != null) {
            evictionExecutor.scheduleWithFixedDelay(
                this::evictIdleObjects,
                evictionRunIntervalMs,
                evictionRunIntervalMs,
                java.util.concurrent.TimeUnit.MILLISECONDS
            );
        }
    }
    
    /**
     * 回收空闲对象
     */
    private void evictIdleObjects() {
        try {
            int currentIdle = idleCount.get();
            if (currentIdle > maxIdle) {
                // 回收多余的空闲对象
                int excess = currentIdle - maxIdle;
                for (int i = 0; i < excess && !pool.isEmpty(); i++) {
                    T obj = pool.poll();
                    if (obj != null) {
                        destroyObject(obj);
                        destroyedCount.incrementAndGet();
                        idleCount.decrementAndGet();
                    }
                }
            } else if (currentIdle < minIdle) {
                // 补充空闲对象到最小值
                int deficit = minIdle - currentIdle;
                for (int i = 0; i < deficit && pool.size() < maxIdle; i++) {
                    T obj = createObject();
                    pool.offer(obj);
                    idleCount.incrementAndGet();
                    createdCount.incrementAndGet();
                }
            }
        } catch (Exception e) {
            System.err.println("对象池回收任务异常: " + e.getMessage());
        }
    }
    
    /**
     * 获取池大小配置
     */
    public int getMaxSize() { return maxSize; }
    public int getMinIdle() { return minIdle; }
    public int getMaxIdle() { return maxIdle; }
    public long getValidationIntervalMs() { return validationIntervalMs; }
    
    /**
     * 关闭池并清理资源
     */
    @Override
    public void close() {
        clear();
        if (evictionExecutor != null && !evictionExecutor.isShutdown()) {
            evictionExecutor.shutdown();
            try {
                if (!evictionExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    evictionExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                evictionExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}