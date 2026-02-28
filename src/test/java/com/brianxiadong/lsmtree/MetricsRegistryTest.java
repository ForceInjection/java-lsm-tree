package com.brianxiadong.lsmtree;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.Assert;
import org.junit.Test;

/**
 * Metrics注册表测试类
 * 测试Micrometer指标注册表的单例模式
 */
public class MetricsRegistryTest {
    @Test
    public void testSingletonRegistry() {
        TestLogger log = new TestLogger("Metrics单例测试");
        log.start("测试MetricsRegistry的单例模式");
        
        log.step("获取两个MeterRegistry实例");
        MeterRegistry a = MetricsRegistry.get();
        MeterRegistry b = MetricsRegistry.get();
        
        log.data("实例a", a.getClass().getSimpleName());
        log.data("实例b", b.getClass().getSimpleName());
        log.data("是否同一实例", a == b);
        
        Assert.assertSame(a, b);
        log.assertSuccess("两次获取的是同一个实例");
        log.pass();
    }
}
