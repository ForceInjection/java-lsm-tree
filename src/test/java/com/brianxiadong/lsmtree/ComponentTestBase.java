package com.brianxiadong.lsmtree;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;

/**
 * 组件测试基类
 * 用于测试单个组件（如 MemTable, SSTable, BloomFilter 等）
 * 提供统一的测试生命周期管理和常用工具方法
 * 
 * 与 LSMTreeTestBase 的区别：
 * - 本基类用于测试独立的组件，不依赖 LSMTree 实例
 * - LSMTreeTestBase 用于测试完整的 LSMTree 功能
 */
public abstract class ComponentTestBase {

    /**
     * JUnit 临时文件夹规则
     * 自动创建和清理测试目录
     */
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /**
     * 测试数据目录
     */
    protected File testDataDir;

    /**
     * 测试日志记录器
     */
    protected TestLogger logger;

    /**
     * 测试前初始化
     * 创建临时目录
     */
    @Before
    public void setUp() throws Exception {
        // 创建临时测试目录
        testDataDir = tempFolder.newFolder(getTestDirName());

        // 初始化日志记录器
        logger = new TestLogger(getClass().getSimpleName());

        System.out.println("\n初始化组件测试环境: 目录=" + testDataDir.getAbsolutePath());
    }

    /**
     * 获取测试目录名称
     * 子类可以覆盖此方法自定义目录名
     * 
     * @return 测试目录名称
     */
    protected String getTestDirName() {
        return "component_test_" + System.currentTimeMillis();
    }

    /**
     * 测试后清理
     */
    @After
    public void tearDown() {
        cleanupResources();
        System.out.println("清理组件测试环境完成");
    }

    /**
     * 清理资源
     * 子类可以覆盖此方法执行自定义清理
     */
    protected void cleanupResources() {
        // 子类可以覆盖此方法执行自定义清理
    }

    // ==================== 常用工具方法 ====================

    /**
     * 获取测试数据目录路径
     * 
     * @return 测试数据目录的绝对路径
     */
    protected String getTestDataDir() {
        return testDataDir.getAbsolutePath();
    }

    /**
     * 在测试目录中创建子目录
     * 
     * @param subDirName 子目录名称
     * @return 子目录的 File 对象
     */
    protected File createSubDir(String subDirName) {
        File subDir = new File(testDataDir, subDirName);
        subDir.mkdirs();
        return subDir;
    }

    /**
     * 在测试目录中创建文件
     * 
     * @param fileName 文件名
     * @return 文件的 File 对象
     */
    protected File createFile(String fileName) {
        return new File(testDataDir, fileName);
    }

    /**
     * 测量操作执行时间
     * 
     * @param operation 操作名称
     * @param runnable  要执行的操作
     * @return 执行时间（毫秒）
     */
    protected long measureTime(String operation, Runnable runnable) {
        long startTime = System.currentTimeMillis();
        runnable.run();
        long duration = System.currentTimeMillis() - startTime;
        System.out.println("操作 [" + operation + "] 耗时: " + duration + "ms");
        return duration;
    }

    /**
     * 测量操作执行时间（纳秒级）
     * 
     * @param operation 操作名称
     * @param runnable  要执行的操作
     * @return 执行时间（纳秒）
     */
    protected long measureTimeNanos(String operation, Runnable runnable) {
        long startTime = System.nanoTime();
        runnable.run();
        long duration = System.nanoTime() - startTime;
        System.out.printf("操作 [%s] 耗时: %.2f ms%n", operation, duration / 1_000_000.0);
        return duration;
    }

    /**
     * 生成测试用的 key
     * 
     * @param index 索引
     * @return key 字符串
     */
    protected String key(int index) {
        return "key" + index;
    }

    /**
     * 生成测试用的 value
     * 
     * @param index 索引
     * @return value 字符串
     */
    protected String value(int index) {
        return "value" + index;
    }

    /**
     * 生成带前缀的测试用的 key
     * 
     * @param prefix 前缀
     * @param index  索引
     * @return key 字符串
     */
    protected String key(String prefix, int index) {
        return prefix + index;
    }

    /**
     * 生成带前缀的测试用的 value
     * 
     * @param prefix 前缀
     * @param index  索引
     * @return value 字符串
     */
    protected String value(String prefix, int index) {
        return prefix + index;
    }

    /**
     * 计算吞吐量
     * 
     * @param operations 操作数量
     * @param durationMs 耗时（毫秒）
     * @return 吞吐量（ops/sec）
     */
    protected double calculateThroughput(long operations, long durationMs) {
        if (durationMs == 0) {
            return 0;
        }
        return operations / (durationMs / 1000.0);
    }

    /**
     * 打印性能统计
     * 
     * @param operation  操作名称
     * @param operations 操作数量
     * @param durationMs 耗时（毫秒）
     */
    protected void printPerformanceStats(String operation, long operations, long durationMs) {
        double throughput = calculateThroughput(operations, durationMs);
        System.out.printf("[%s] 操作数: %,d | 耗时: %,d ms | 吞吐量: %,.0f ops/sec%n",
                operation, operations, durationMs, throughput);
    }
}
