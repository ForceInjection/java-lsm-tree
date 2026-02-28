package com.brianxiadong.lsmtree.memory;

/**
 * 对象池统计信息
 */
public class PoolStats {
    private final int activeCount;
    private final int idleCount;
    private final int totalCount;
    private final long borrowedCount;
    private final long returnedCount;
    private final long createdCount;
    private final long destroyedCount;
    
    public PoolStats(int activeCount, int idleCount, int totalCount,
                    long borrowedCount, long returnedCount, 
                    long createdCount, long destroyedCount) {
        this.activeCount = activeCount;
        this.idleCount = idleCount;
        this.totalCount = totalCount;
        this.borrowedCount = borrowedCount;
        this.returnedCount = returnedCount;
        this.createdCount = createdCount;
        this.destroyedCount = destroyedCount;
    }
    
    // Getters
    public int getActiveCount() { return activeCount; }
    public int getIdleCount() { return idleCount; }
    public int getTotalCount() { return totalCount; }
    public long getBorrowedCount() { return borrowedCount; }
    public long getReturnedCount() { return returnedCount; }
    public long getCreatedCount() { return createdCount; }
    public long getDestroyedCount() { return destroyedCount; }
    
    /**
     * 获取池使用率
     */
    public double getUsageRatio() {
        return totalCount > 0 ? (double) activeCount / totalCount : 0.0;
    }
    
    /**
     * 获取命中率
     */
    public double getHitRate() {
        return borrowedCount > 0 ? (double) returnedCount / borrowedCount : 0.0;
    }
    
    @Override
    public String toString() {
        return String.format(
            "PoolStats{active=%d, idle=%d, total=%d, usage=%.1f%%, hitRate=%.1f%%}",
            activeCount, idleCount, totalCount, getUsageRatio() * 100, getHitRate() * 100
        );
    }
}