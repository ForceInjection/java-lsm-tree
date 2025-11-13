package com.brianxiadong.lsmtree;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

public class MicrometerLSMTreeMetrics implements LSMTreeMetrics {
    private final Timer writeTimer;
    private final Timer readTimer;
    private final DistributionSummary compactionBytes;
    private final Timer flushTimer;
    private final DistributionSummary flushBytes;
    private final Counter compactionFailures;
    private final Counter flushFailures;

    public MicrometerLSMTreeMetrics(String name) {
        MeterRegistry registry = MetricsRegistry.get();
        this.writeTimer = Timer.builder("lsm.write.latency").tag("name", name).register(registry);
        this.readTimer = Timer.builder("lsm.read.latency").tag("name", name).register(registry);
        this.compactionBytes = DistributionSummary.builder("lsm.compaction.bytes").tag("name", name).register(registry);
        this.flushTimer = Timer.builder("lsm.flush.latency").tag("name", name).register(registry);
        this.flushBytes = DistributionSummary.builder("lsm.flush.bytes").tag("name", name).register(registry);
        this.compactionFailures = Counter.builder("lsm.compaction.failures").tag("name", name).register(registry);
        this.flushFailures = Counter.builder("lsm.flush.failures").tag("name", name).register(registry);
    }

    @Override
    public void recordWrite(long latencyNanos) {
        writeTimer.record(latencyNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordRead(long latencyNanos) {
        readTimer.record(latencyNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordCompaction(long durationNanos, long bytesCompacted) {
        compactionBytes.record(bytesCompacted);
    }

    @Override
    public void recordFlush(long durationNanos, long bytesFlushed) {
        flushTimer.record(durationNanos, TimeUnit.NANOSECONDS);
        flushBytes.record(bytesFlushed);
    }

    @Override
    public void recordCompactionFailure() {
        compactionFailures.increment();
    }

    @Override
    public void recordFlushFailure() {
        flushFailures.increment();
    }
}
