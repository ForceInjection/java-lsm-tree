package com.brianxiadong.lsmtree.memory;

/**
 * 内存使用统计信息
 */
public class MemoryUsageStats {
    private final long heapUsed;
    private final long heapMax;
    private final long offHeapUsed;
    private final long offHeapMax;
    private final long gcCount;
    private final long gcTime;
    private final double gcPauseAvg;
    
    public MemoryUsageStats(long heapUsed, long heapMax, long offHeapUsed, 
                           long offHeapMax, long gcCount, long gcTime, double gcPauseAvg) {
        this.heapUsed = heapUsed;
        this.heapMax = heapMax;
        this.offHeapUsed = offHeapUsed;
        this.offHeapMax = offHeapMax;
        this.gcCount = gcCount;
        this.gcTime = gcTime;
        this.gcPauseAvg = gcPauseAvg;
    }
    
    // Getters
    public long getHeapUsed() { return heapUsed; }
    public long getHeapMax() { return heapMax; }
    public long getOffHeapUsed() { return offHeapUsed; }
    public long getOffHeapMax() { return offHeapMax; }
    public long getGcCount() { return gcCount; }
    public long getGcTime() { return gcTime; }
    public double getGcPauseAvg() { return gcPauseAvg; }
    
    /**
     * 获取堆内存使用率
     */
    public double getHeapUsageRatio() {
        return heapMax > 0 ? (double) heapUsed / heapMax : 0.0;
    }
    
    /**
     * 获取堆外内存使用率
     */
    public double getOffHeapUsageRatio() {
        return offHeapMax > 0 ? (double) offHeapUsed / offHeapMax : 0.0;
    }
    
    @Override
    public String toString() {
        return String.format(
            "MemoryUsageStats{heap=%.1f%%, offHeap=%.1f%%, gcCount=%d, gcTime=%dms, avgPause=%.2fms}",
            getHeapUsageRatio() * 100, getOffHeapUsageRatio() * 100, 
            gcCount, gcTime, gcPauseAvg
        );
    }
}