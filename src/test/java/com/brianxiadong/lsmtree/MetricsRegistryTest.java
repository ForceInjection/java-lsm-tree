package com.brianxiadong.lsmtree;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.Assert;
import org.junit.Test;

public class MetricsRegistryTest {
    @Test
    public void testSingletonRegistry() {
        MeterRegistry a = MetricsRegistry.get();
        MeterRegistry b = MetricsRegistry.get();
        Assert.assertSame(a, b);
    }
}

