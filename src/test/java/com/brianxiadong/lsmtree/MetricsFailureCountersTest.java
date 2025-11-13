package com.brianxiadong.lsmtree;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import org.junit.Assert;
import org.junit.Test;

public class MetricsFailureCountersTest {
    @Test
    public void testFailureCounters() {
        MicrometerLSMTreeMetrics m = new MicrometerLSMTreeMetrics("test");
        m.recordFlushFailure();
        m.recordCompactionFailure();
        MeterRegistry r = MetricsRegistry.get();
        Counter cf = r.find("lsm.compaction.failures").tag("name","test").counter();
        Counter ff = r.find("lsm.flush.failures").tag("name","test").counter();
        Assert.assertNotNull(cf);
        Assert.assertNotNull(ff);
        Assert.assertTrue(cf.count() >= 1.0);
        Assert.assertTrue(ff.count() >= 1.0);
    }
}
