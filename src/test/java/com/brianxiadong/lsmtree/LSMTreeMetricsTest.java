package com.brianxiadong.lsmtree;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.Assert;
import org.junit.Test;

/**
 * LSM Tree指标测试类
 * 测试Micrometer指标的记录功能
 */
public class LSMTreeMetricsTest {
    @Test
    public void testWriteAndReadMetricsRecorded() throws Exception {
        TestLogger log = new TestLogger("LSM Tree指标测试");
        log.start("测试写和读操作的指标记录");
        
        log.step("创建LSMTree并执行20次写入");
        LSMTree tree = new LSMTree(TestConfig.getPerformanceTestDataPath("metrics"), 10);
        for (int i = 0; i < 20; i++) {
            tree.put("k" + i, "v" + i);
        }
        log.data("写入次数", 20);
        
        log.step("执行20次读取");
        for (int i = 0; i < 20; i++) {
            tree.get("k" + i);
        }
        log.data("读取次数", 20);
        
        log.step("检查指标记录");
        MeterRegistry registry = MetricsRegistry.get();
        Timer wt = registry.find("lsm.write.latency").timer();
        Timer rt = registry.find("lsm.read.latency").timer();
        
        log.data("写入Timer", wt != null ? "存在" : "不存在");
        log.data("读取Timer", rt != null ? "存在" : "不存在");
        Assert.assertNotNull(wt);
        Assert.assertNotNull(rt);
        
        log.data("写入操作计数", wt.count());
        log.data("读取操作计数", rt.count());
        Assert.assertTrue(wt.count() >= 20);
        Assert.assertTrue(rt.count() >= 20);
        log.assertSuccess("指标正确记录了写和读操作");
        tree.close();
        log.pass();
    }
}
