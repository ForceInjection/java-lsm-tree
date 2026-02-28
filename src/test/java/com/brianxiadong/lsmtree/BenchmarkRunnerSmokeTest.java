package com.brianxiadong.lsmtree;

import org.junit.Before;
import org.junit.Test;
import java.io.File;

/**
 * BenchmarkRunner 原子化单元测试
 * 每个测试方法对应一个独立的基准测试能力
 */
public class BenchmarkRunnerSmokeTest {

    private BenchmarkRunner runner;
    private String testDataDir;

    @Before
    public void setUp() {
        testDataDir = TestConfig.getPerformanceTestDataPath("junit-atomic-test");
        new File(testDataDir).mkdirs();

        BenchmarkRunner.BenchmarkConfig config = new BenchmarkRunner.BenchmarkConfig(
                20, // 小规模操作数用于单元测试
                8, // 键大小
                16, // 值大小
                testDataDir,
                System.currentTimeMillis());
        runner = new BenchmarkRunner(config);
    }

    @Test
    public void testSequentialWrites() {
        TestLogger log = new TestLogger("顺序写入性能测试");
        log.start("测试BenchmarkRunner的顺序写入基准测试能力");
        log.step("执行顺序写入基准测试");
        runner.benchmarkSequentialWrites();
        log.data("操作数", 20);
        log.assertSuccess("顺序写入基准测试完成");
        log.pass();
    }

    @Test
    public void testRandomWrites() {
        TestLogger log = new TestLogger("随机写入性能测试");
        log.start("测试BenchmarkRunner的随机写入基准测试能力");
        log.step("执行随机写入基准测试");
        runner.benchmarkRandomWrites();
        log.data("操作数", 20);
        log.assertSuccess("随机写入基准测试完成");
        log.pass();
    }

    @Test
    public void testReads() {
        TestLogger log = new TestLogger("读取性能测试");
        log.start("测试BenchmarkRunner的读取基准测试能力");
        log.step("执行读取基准测试");
        runner.benchmarkReads();
        log.data("操作数", 20);
        log.assertSuccess("读取基准测试完成");
        log.pass();
    }

    @Test
    public void testMixedWorkload() {
        TestLogger log = new TestLogger("混合工作负载测试");
        log.start("测试BenchmarkRunner的混合读写工作负载能力");
        log.step("执行混合工作负载基准测试");
        runner.benchmarkMixedWorkload();
        log.data("操作数", 20);
        log.assertSuccess("混合工作负载基准测试完成");
        log.pass();
    }

    @Test
    public void testWriteLatency() {
        TestLogger log = new TestLogger("写入延迟分析测试");
        log.start("测试BenchmarkRunner的写入延迟分析能力");
        log.step("执行写入延迟基准测试");
        runner.benchmarkWriteLatency();
        log.data("操作数", 20);
        log.assertSuccess("写入延迟分析完成");
        log.pass();
    }

    @Test
    public void testMemTableFlushImpact() {
        TestLogger log = new TestLogger("MemTable刷盘影响测试");
        log.start("测试MemTable刷盘对性能的影响");
        log.step("执行MemTable刷盘基准测试");
        runner.benchmarkMemTableFlushImpact();
        log.data("测试目录", testDataDir);
        log.assertSuccess("MemTable刷盘影响测试完成");
        log.pass();
    }

    @Test
    public void testConcurrentOperations() {
        TestLogger log = new TestLogger("并发操作性能测试");
        log.start("测试BenchmarkRunner的并发操作基准测试能力");
        log.step("执行并发操作基准测试");
        runner.benchmarkConcurrentOperations();
        log.data("操作数", 20);
        log.assertSuccess("并发操作基准测试完成");
        log.pass();
    }

    @Test
    public void testDeleteOperations() {
        TestLogger log = new TestLogger("删除操作性能测试");
        log.start("测试BenchmarkRunner的删除操作基准测试能力");
        log.step("执行删除操作基准测试");
        runner.benchmarkDeleteOperations();
        log.data("操作数", 20);
        log.assertSuccess("删除操作基准测试完成");
        log.pass();
    }

    @Test
    public void testRangeQueries() {
        TestLogger log = new TestLogger("范围查询性能测试");
        log.start("测试BenchmarkRunner的范围查询基准测试能力");
        log.step("执行范围查询基准测试");
        runner.benchmarkRangeQueries();
        log.data("操作数", 20);
        log.assertSuccess("范围查询基准测试完成");
        log.pass();
    }

    @Test
    public void testAsyncVsSyncIO() {
        TestLogger log = new TestLogger("异步vs同步I/O对比测试");
        log.start("测试异步I/O与同步I/O的性能对比");
        log.step("执行异步vs同步I/O基准测试");
        runner.benchmarkAsyncVsSyncIO();
        log.data("对比类型", "Async vs Sync");
        log.assertSuccess("异步vs同步I/O对比完成");
        log.pass();
    }

    @Test
    public void testAsyncVsSyncCurve() {
        TestLogger log = new TestLogger("异步vs同步并发曲线测试");
        log.start("测试不同并发级别下异步与同步I/O的性能曲线");
        log.step("执行异步vs同步并发曲线基准测试");
        runner.benchmarkAsyncVsSyncCurve();
        log.data("测试类型", "并发曲线");
        log.assertSuccess("异步并发曲线测试完成");
        log.pass();
    }

    @Test
    public void testCacheVsNoCache() {
        TestLogger log = new TestLogger("缓存vs无缓存对比测试");
        log.start("测试启用缓存与不启用缓存的性能对比");
        log.step("执行缓存vs无缓存基准测试");
        runner.benchmarkCacheVsNoCache();
        log.data("对比类型", "Cache vs NoCache");
        log.assertSuccess("缓存对比测试完成");
        log.pass();
    }
}
