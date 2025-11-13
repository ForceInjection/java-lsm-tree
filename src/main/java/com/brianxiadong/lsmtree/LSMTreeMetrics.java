package com.brianxiadong.lsmtree;

public interface LSMTreeMetrics {
    void recordWrite(long latencyNanos);
    void recordRead(long latencyNanos);
    void recordCompaction(long durationNanos, long bytesCompacted);
    void recordFlush(long durationNanos, long bytesFlushed);
    void recordCompactionFailure();
    void recordFlushFailure();
}
