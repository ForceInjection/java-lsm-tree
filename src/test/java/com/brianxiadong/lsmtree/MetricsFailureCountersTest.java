package com.brianxiadong.lsmtree;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import org.junit.Assert;
import org.junit.Test;

/**
 * Metrics失败计数器测试类
 * 测试刷盘和压缩失败计数器
 */
public class MetricsFailureCountersTest {
    @Test
    public void testFailureCounters() {
        TestLogger log = new TestLogger("失败计数器测试");
        log.start("测试刷盘和压缩失败计数器");
        
        log.step("创建MicrometerLSMTreeMetrics");
        MicrometerLSMTreeMetrics m = new MicrometerLSMTreeMetrics("test");
        
        log.step("记录刷盘失败和压缩失败");
        m.recordFlushFailure();
        m.recordCompactionFailure();
        log.data("记录", "1次刷盘失败, 1次压缩失败");
        
        log.step("检查计数器");
        MeterRegistry r = MetricsRegistry.get();
        Counter cf = r.find("lsm.compaction.failures").tag("name","test").counter();
        Counter ff = r.find("lsm.flush.failures").tag("name","test").counter();
        
        log.data("compaction.failures计数器存在", cf != null);
        log.data("flush.failures计数器存在", ff != null);
        Assert.assertNotNull(cf);
        Assert.assertNotNull(ff);
        
        log.data("compaction.failures值", cf.count());
        log.data("flush.failures值", ff.count());
        Assert.assertTrue(cf.count() >= 1.0);
        Assert.assertTrue(ff.count() >= 1.0);
        log.assertSuccess("失败计数器正确记录");
        log.pass();
    }
}
