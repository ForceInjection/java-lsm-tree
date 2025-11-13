package com.brianxiadong.lsmtree;

import org.junit.Test;

public class BenchmarkRunnerSmokeTest {
    @Test
    public void testRunAllBenchmarksQuick() {
        String[] args = new String[]{
                "--operations", "200",
                "--threads", "1",
                "--key-size", "8",
                "--value-size", "16",
                "--data-dir", TestConfig.getPerformanceTestDataPath("junit-bench")
        };
        BenchmarkRunner.main(args);
    }
}

