package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MetricsHttpServerTest {
    @Test
    public void testMetricsEndpoint() throws Exception {
        System.setProperty("lsm.metrics.http.enabled", "true");
        System.setProperty("lsm.metrics.http.port", "9092");
        LSMTree tree = new LSMTree(TestConfig.getFunctionalTestDataPath("metrics-http"), 10);
        tree.put("a", "1");
        Thread.sleep(100);
        URL url = new URL("http://127.0.0.1:9092/metrics");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);
        int code = conn.getResponseCode();
        Assert.assertEquals(200, code);
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        String line;
        boolean hasMetric = false;
        while ((line = br.readLine()) != null) {
            if (line.contains("lsm_memtable_size")) { hasMetric = true; break; }
        }
        br.close();
        tree.close();
        Assert.assertTrue(hasMetric);
    }
}

