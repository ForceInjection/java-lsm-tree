package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Metrics HTTP服务器测试类
 * 测试指标HTTP端点
 */
public class MetricsHttpServerTest {
    @Test
    public void testMetricsEndpoint() throws Exception {
        TestLogger log = new TestLogger("Metrics HTTP端点测试");
        log.start("测试指标HTTP端点正常工作");
        
        log.step("启用HTTP指标服务器（端口9092）");
        System.setProperty("lsm.metrics.http.enabled", "true");
        System.setProperty("lsm.metrics.http.port", "9092");
        log.data("端口", 9092);
        
        LSMTree tree = new LSMTree(TestConfig.getFunctionalTestDataPath("metrics-http"), 10);
        tree.put("a", "1");
        Thread.sleep(100);
        
        log.step("请求/metrics端点");
        URL url = new URL("http://127.0.0.1:9092/metrics");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);
        int code = conn.getResponseCode();
        log.data("HTTP响应码", code);
        Assert.assertEquals(200, code);
        
        log.step("验证指标内容");
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        String line;
        boolean hasMetric = false;
        while ((line = br.readLine()) != null) {
            if (line.contains("lsm_memtable_size")) { hasMetric = true; break; }
        }
        br.close();
        log.data("包含lsm_memtable_size指标", hasMetric);
        
        tree.close();
        Assert.assertTrue(hasMetric);
        log.assertSuccess("HTTP指标端点正常工作");
        log.pass();
    }
}

