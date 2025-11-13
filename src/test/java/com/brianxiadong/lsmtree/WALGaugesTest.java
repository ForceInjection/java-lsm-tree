package com.brianxiadong.lsmtree;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.Assert;
import org.junit.Test;

public class WALGaugesTest {
    @Test
    public void testWALSizeGaugeChanges() throws Exception {
        System.setProperty("lsm.metrics.http.enabled", "false");
        LSMTree tree = new LSMTree(TestConfig.getPerformanceTestDataPath("wal-gauge"), 10);
        MeterRegistry r = MetricsRegistry.get();
        Gauge g = r.find("lsm.wal.size.bytes").gauge();
        Assert.assertNotNull(g);
        tree.put("k1","v1");
        tree.put("k2","v2");
        Thread.sleep(50);
        Assert.assertNotNull(g.value());
        for (int i = 0; i < 20; i++) tree.put("kY"+i, "vy");
        Thread.sleep(50);
        tree.close();
    }
}
