package com.brianxiadong.lsmtree;

public class NoopLSMTreeMetrics implements LSMTreeMetrics {
    @Override
    public void recordWrite(long latencyNanos) {}

    @Override
    public void recordRead(long latencyNanos) {}

    @Override
    public void recordCompaction(long durationNanos, long bytesCompacted) {}

    @Override
    public void recordFlush(long durationNanos, long bytesFlushed) {}

    @Override
    public void recordCompactionFailure() {}

    @Override
    public void recordFlushFailure() {}
}
