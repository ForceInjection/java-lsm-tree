package com.brianxiadong.lsmtree.memory;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存监控器
 */
public class MemoryMonitor {
    private final MemoryMXBean memoryBean;
    private final List<GarbageCollectorMXBean> gcBeans;
    private final ScheduledExecutorService scheduler;
    private final AtomicLong gcCount = new AtomicLong(0);
    private final AtomicLong gcTime = new AtomicLong(0);
    
    private volatile boolean monitoring = false;
    
    public MemoryMonitor() {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "memory-monitor");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * 开始监控
     */
    public void startMonitoring() {
        if (monitoring) return;
        
        monitoring = true;
        scheduler.scheduleAtFixedRate(this::collectMetrics, 0, 5, TimeUnit.SECONDS);
        System.out.println("内存监控已启动");
    }
    
    /**
     * 停止监控
     */
    public void stopMonitoring() {
        monitoring = false;
        System.out.println("内存监控已停止");
    }
    
    /**
     * 收集内存指标
     */
    private void collectMetrics() {
        if (!monitoring) return;
        
        try {
            // 收集GC统计
            long totalGcCount = 0;
            long totalGcTime = 0;
            
            for (GarbageCollectorMXBean gcBean : gcBeans) {
                totalGcCount += gcBean.getCollectionCount();
                totalGcTime += gcBean.getCollectionTime();
            }
            
            gcCount.set(totalGcCount);
            gcTime.set(totalGcTime);
            
        } catch (Exception e) {
            System.err.println("内存监控收集失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取内存使用统计
     */
    public MemoryUsageStats getMemoryStats() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        
        return new MemoryUsageStats(
            heapUsage.getUsed(),
            heapUsage.getMax(),
            nonHeapUsage.getUsed(),
            nonHeapUsage.getMax(),
            gcCount.get(),
            gcTime.get(),
            calculateAverageGCPause()
        );
    }
    
    /**
     * 计算平均GC暂停时间
     */
    private double calculateAverageGCPause() {
        long count = gcCount.get();
        long time = gcTime.get();
        return count > 0 ? (double) time / count : 0.0;
    }
    
    /**
     * 记录GC周期
     */
    public void recordGCCycle() {
        gcCount.incrementAndGet();
    }
    
    /**
     * 清理资源
     */
    public void shutdown() {
        stopMonitoring();
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 打印详细内存信息
     */
    public void printDetailedMemoryInfo() {
        MemoryUsageStats stats = getMemoryStats();
        System.out.println("=== 内存使用详情 ===");
        System.out.println(stats);
        
        // 打印各代内存使用情况
        ManagementFactory.getMemoryPoolMXBeans().forEach(pool -> {
            System.out.printf("内存池 %s: %s%n", 
                pool.getName(), pool.getUsage());
        });
        
        // 打印GC信息
        gcBeans.forEach(gc -> {
            System.out.printf("GC %s: 次数=%d, 时间=%dms%n",
                gc.getName(), gc.getCollectionCount(), gc.getCollectionTime());
        });
    }
}