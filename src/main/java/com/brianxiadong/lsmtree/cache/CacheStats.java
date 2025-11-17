package com.brianxiadong.lsmtree.cache;

public class CacheStats {
    private volatile long hits;
    private volatile long misses;
    private volatile long evictions;
    private volatile int size;
    private volatile int capacity;

    public synchronized void recordHit() { hits++; }
    public synchronized void recordMiss() { misses++; }
    public synchronized void recordEviction() { evictions++; }
    public synchronized void setSize(int s) { size = s; }
    public synchronized void setCapacity(int c) { capacity = c; }

    public long getHits() { return hits; }
    public long getMisses() { return misses; }
    public long getEvictions() { return evictions; }
    public int getSize() { return size; }
    public int getCapacity() { return capacity; }
    public double getHitRatio() {
        long total = hits + misses;
        return total == 0 ? 0.0 : (double) hits / (double) total;
    }
}