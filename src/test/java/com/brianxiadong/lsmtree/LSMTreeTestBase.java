package com.brianxiadong.lsmtree;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

/**
 * LSM Tree 测试基类
 * 提供统一的测试生命周期管理和常用工具方法
 * 
 * 所有需要测试 LSMTree 的测试类都应该继承此类
 */
public abstract class LSMTreeTestBase {
    
    /**
     * JUnit 临时文件夹规则
     * 自动创建和清理测试目录
     */
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
    
    /**
     * 被测试的 LSMTree 实例
     * 子类可以直接使用
     */
    protected LSMTree lsmTree;
    
    /**
     * 测试数据目录
     */
    protected File testDataDir;
    
    /**
     * 测试日志记录器
     */
    protected TestLogger logger;
    
    /**
     * 获取默认的 MemTable 大小
     * 子类可以覆盖此方法自定义配置
     * 
     * @return MemTable 大小阈值
     */
    protected int getDefaultMemTableSize() {
        return 100;
    }
    
    /**
     * 测试前初始化
     * 创建临时目录和 LSMTree 实例
     */
    @Before
    public void setUp() throws Exception {
        // 创建临时测试目录
        testDataDir = tempFolder.newFolder("lsm_test_" + System.currentTimeMillis());
        
        // 创建 LSMTree 实例
        lsmTree = createLSMTree();
        
        // 初始化日志记录器
        logger = new TestLogger(getClass().getSimpleName());
        
        System.out.println("\n初始化测试环境: 目录=" + testDataDir.getAbsolutePath() + 
                          ", MemTable大小=" + getDefaultMemTableSize());
    }
    
    /**
     * 测试后清理
     * 使用 try-finally 确保资源被释放
     */
    @After
    public void tearDown() {
        cleanupResources();
        System.out.println("清理测试环境完成");
    }
    
    /**
     * 创建 LSMTree 实例
     * 子类可以覆盖此方法自定义创建逻辑
     * 
     * @return 新的 LSMTree 实例
     * @throws IOException 当创建失败时
     */
    protected LSMTree createLSMTree() throws IOException {
        return new LSMTree(testDataDir.getAbsolutePath(), getDefaultMemTableSize());
    }
    
    /**
     * 清理资源
     * 使用 try-finally 确保即使 close() 失败也能继续清理
     */
    protected void cleanupResources() {
        if (lsmTree != null) {
            try {
                lsmTree.close();
            } catch (IOException e) {
                System.err.println("关闭 LSMTree 时发生错误: " + e.getMessage());
                e.printStackTrace();
            } finally {
                lsmTree = null;
            }
        }
    }
    
    // ==================== 常用工具方法 ====================
    
    /**
     * 批量插入测试数据
     * 
     * @param start 起始索引
     * @param count 插入数量
     * @throws IOException 当写入失败时
     */
    protected void bulkInsert(int start, int count) throws IOException {
        for (int i = start; i < start + count; i++) {
            lsmTree.put("key" + i, "value" + i);
        }
    }
    
    /**
     * 批量插入测试数据（带自定义前缀）
     * 
     * @param prefix key 前缀
     * @param start 起始索引
     * @param count 插入数量
     * @throws IOException 当写入失败时
     */
    protected void bulkInsert(String prefix, int start, int count) throws IOException {
        for (int i = start; i < start + count; i++) {
            lsmTree.put(prefix + i, "value" + i);
        }
    }
    
    /**
     * 验证数据范围完整性
     * 
     * @param start 起始索引
     * @param count 验证数量
     * @throws IOException 当读取失败时
     */
    protected void verifyDataRange(int start, int count) throws IOException {
        for (int i = start; i < start + count; i++) {
            String expectedValue = "value" + i;
            String actualValue = lsmTree.get("key" + i);
            if (!expectedValue.equals(actualValue)) {
                throw new AssertionError("数据验证失败: key" + i + 
                    " 期望值=" + expectedValue + ", 实际值=" + actualValue);
            }
        }
    }
    
    /**
     * 断言键值对相等
     * 
     * @param key 键
     * @param expectedValue 期望值
     * @throws IOException 当读取失败时
     */
    protected void assertKeyValueEquals(String key, String expectedValue) throws IOException {
        String actualValue = lsmTree.get(key);
        if (!expectedValue.equals(actualValue)) {
            throw new AssertionError("键值验证失败: key=" + key + 
                " 期望值=" + expectedValue + ", 实际值=" + actualValue);
        }
    }
    
    /**
     * 断言键不存在
     * 
     * @param key 键
     * @throws IOException 当读取失败时
     */
    protected void assertKeyNotExists(String key) throws IOException {
        String value = lsmTree.get(key);
        if (value != null) {
            throw new AssertionError("键应该不存在: key=" + key + ", 但实际值=" + value);
        }
    }
    
    /**
     * 执行刷盘并验证数据完整性
     * 
     * @param start 数据起始索引
     * @param count 数据数量
     * @throws IOException 当操作失败时
     */
    protected void flushAndVerify(int start, int count) throws IOException {
        lsmTree.flush();
        verifyDataRange(start, count);
    }
    
    /**
     * 关闭并重新打开 LSMTree，验证数据持久化
     * 
     * @param start 数据起始索引
     * @param count 数据数量
     * @throws IOException 当操作失败时
     */
    protected void reopenAndVerify(int start, int count) throws IOException {
        // 关闭当前实例
        lsmTree.close();
        
        // 重新打开
        lsmTree = new LSMTree(testDataDir.getAbsolutePath(), getDefaultMemTableSize());
        
        // 验证数据
        verifyDataRange(start, count);
    }
    
    /**
     * 获取测试数据目录路径
     * 
     * @return 测试数据目录的绝对路径
     */
    protected String getTestDataDir() {
        return testDataDir.getAbsolutePath();
    }
    
    /**
     * 测量操作执行时间
     * 
     * @param operation 操作名称
     * @param runnable 要执行的操作
     * @return 执行时间（毫秒）
     */
    protected long measureTime(String operation, Runnable runnable) {
        long startTime = System.currentTimeMillis();
        runnable.run();
        long duration = System.currentTimeMillis() - startTime;
        System.out.println("操作 [" + operation + "] 耗时: " + duration + "ms");
        return duration;
    }
}
