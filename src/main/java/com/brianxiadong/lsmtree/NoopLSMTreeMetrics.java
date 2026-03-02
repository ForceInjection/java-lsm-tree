package com.brianxiadong.lsmtree;

/**
 * 空操作 (No-op) 指标实现
 * <p>
 * 用于不需要监控或测试环境，所有指标记录操作均为空方法。
 * 避免了 null 检查。
 */
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
