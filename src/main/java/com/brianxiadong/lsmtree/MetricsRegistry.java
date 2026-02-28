package com.brianxiadong.lsmtree;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.distribution.pause.NoPauseDetector;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

/**
 * Metrics 注册表单例
 * 使用 NoPauseDetector 避免 LatencyUtils 创建后台线程
 */
public class MetricsRegistry {
    private static volatile PrometheusMeterRegistry REGISTRY;
    
    public static MeterRegistry get() {
        if (REGISTRY == null) {
            synchronized (MetricsRegistry.class) {
                if (REGISTRY == null) {
                    REGISTRY = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
                    REGISTRY.config().pauseDetector(new NoPauseDetector());
                }
            }
        }
        return REGISTRY;
    }
}

