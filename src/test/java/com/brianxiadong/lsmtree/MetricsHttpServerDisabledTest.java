package com.brianxiadong.lsmtree;

import org.junit.Test;

public class MetricsHttpServerDisabledTest {
    @Test
    public void testStartDisabledNoSideEffect() {
        System.clearProperty("lsm.metrics.http.enabled");
        // 未启用时应直接返回，不改变全局状态
        MetricsHttpServer.startIfEnabled();
    }
}
