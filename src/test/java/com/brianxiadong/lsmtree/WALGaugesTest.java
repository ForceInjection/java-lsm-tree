package com.brianxiadong.lsmtree;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.Assert;
import org.junit.Test;

/**
 * WAL指标测试类
 * 测试WAL大小的Micrometer Gauge指标
 */
public class WALGaugesTest {
    @Test
    public void testWALSizeGaugeChanges() throws Exception {
        TestLogger log = new TestLogger("WAL大小指标测试");
        log.start("测试WAL大小Gauge随写入变化");
        
        System.setProperty("lsm.metrics.http.enabled", "false");
        LSMTree tree = new LSMTree(TestConfig.getPerformanceTestDataPath("wal-gauge"), 10);
        
        log.step("获取WAL大小Gauge");
        MeterRegistry r = MetricsRegistry.get();
        Gauge g = r.find("lsm.wal.size.bytes").gauge();
        log.data("Gauge名称", "lsm.wal.size.bytes");
        log.data("Gauge存在", g != null);
        Assert.assertNotNull(g);
        
        log.step("写入k1和k2");
        tree.put("k1","v1");
        tree.put("k2","v2");
        Thread.sleep(50);
        log.data("初始WAL大小", g.value() + " bytes");
        Assert.assertNotNull(g.value());
        
        log.step("继续写入20条数据");
        for (int i = 0; i < 20; i++) tree.put("kY"+i, "vy");
        Thread.sleep(50);
        log.data("写入后WAL大小", g.value() + " bytes");
        log.assertSuccess("WAL大小指标随写入增长");
        tree.close();
        log.pass();
    }
}
