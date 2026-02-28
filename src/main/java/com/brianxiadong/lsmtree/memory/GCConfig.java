package com.brianxiadong.lsmtree.memory;

/**
 * GC配置类
 */
public class GCConfig {
    private String collectorType; // G1GC, CMS, ParallelGC等
    private int heapSizeMB;
    private int maxGCPauseMillis;
    private boolean useStringDeduplication;
    private int g1HeapRegionSizeMB;
    private boolean useCompressedOops;
    
    public GCConfig() {
        this.collectorType = "G1GC";
        this.heapSizeMB = 4096; // 4GB
        this.maxGCPauseMillis = 200;
        this.useStringDeduplication = true;
        this.g1HeapRegionSizeMB = 16;
        this.useCompressedOops = true;
    }
    
    // Getters and Setters
    public String getCollectorType() { return collectorType; }
    public void setCollectorType(String collectorType) { this.collectorType = collectorType; }
    
    public int getHeapSizeMB() { return heapSizeMB; }
    public void setHeapSizeMB(int heapSizeMB) { this.heapSizeMB = heapSizeMB; }
    
    public int getMaxGCPauseMillis() { return maxGCPauseMillis; }
    public void setMaxGCPauseMillis(int maxGCPauseMillis) { this.maxGCPauseMillis = maxGCPauseMillis; }
    
    public boolean isUseStringDeduplication() { return useStringDeduplication; }
    public void setUseStringDeduplication(boolean useStringDeduplication) { this.useStringDeduplication = useStringDeduplication; }
    
    public int getG1HeapRegionSizeMB() { return g1HeapRegionSizeMB; }
    public void setG1HeapRegionSizeMB(int g1HeapRegionSizeMB) { this.g1HeapRegionSizeMB = g1HeapRegionSizeMB; }
    
    public boolean isUseCompressedOops() { return useCompressedOops; }
    public void setUseCompressedOops(boolean useCompressedOops) { this.useCompressedOops = useCompressedOops; }
    
    /**
     * 生成JVM启动参数
     */
    public String[] toJvmArgs() {
        return new String[] {
            "-XX:+" + collectorType,
            "-Xmx" + heapSizeMB + "m",
            "-Xms" + heapSizeMB + "m",
            "-XX:MaxGCPauseMillis=" + maxGCPauseMillis,
            "-XX:+UseStringDeduplication=" + useStringDeduplication,
            "-XX:G1HeapRegionSize=" + g1HeapRegionSizeMB + "m",
            "-XX:+UseCompressedOops=" + useCompressedOops
        };
    }
    
    @Override
    public String toString() {
        return String.format(
            "GCConfig{collector=%s, heap=%dMB, maxPause=%dms, dedup=%s, region=%dMB}",
            collectorType, heapSizeMB, maxGCPauseMillis, useStringDeduplication, g1HeapRegionSizeMB
        );
    }
}