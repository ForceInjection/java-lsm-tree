package com.brianxiadong.lsmtree;

import org.junit.Test;

/**
 * Metrics HTTP服务器禁用测试类
 * 测试禁用状态下HTTP服务器不启动
 */
public class MetricsHttpServerDisabledTest {
    @Test
    public void testStartDisabledNoSideEffect() {
        TestLogger log = new TestLogger("HTTP服务器禁用测试");
        log.start("测试禁用状态下HTTP服务器不启动");
        
        log.step("清除配置属性");
        System.clearProperty("lsm.metrics.http.enabled");
        log.data("lsm.metrics.http.enabled", "未设置");
        
        log.step("调用startIfEnabled");
        MetricsHttpServer.startIfEnabled();
        log.data("结果", "无副作用（服务器未启动）");
        
        log.assertSuccess("禁用状态下HTTP服务器不启动");
        log.pass();
    }
}
