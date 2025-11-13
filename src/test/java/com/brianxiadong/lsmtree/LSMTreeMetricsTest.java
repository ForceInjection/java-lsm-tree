package com.brianxiadong.lsmtree;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.Assert;
import org.junit.Test;

public class LSMTreeMetricsTest {
    @Test
    public void testWriteAndReadMetricsRecorded() throws Exception {
        LSMTree tree = new LSMTree(TestConfig.getPerformanceTestDataPath("metrics"), 10);
        for (int i = 0; i < 20; i++) {
            tree.put("k" + i, "v" + i);
        }
        for (int i = 0; i < 20; i++) {
            tree.get("k" + i);
        }
        MeterRegistry registry = MetricsRegistry.get();
        Timer wt = registry.find("lsm.write.latency").timer();
        Timer rt = registry.find("lsm.read.latency").timer();
        Assert.assertNotNull(wt);
        Assert.assertNotNull(rt);
        Assert.assertTrue(wt.count() >= 20);
        Assert.assertTrue(rt.count() >= 20);
        tree.close();
    }
}

