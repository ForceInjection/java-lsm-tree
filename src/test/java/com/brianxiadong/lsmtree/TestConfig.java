package com.brianxiadong.lsmtree;

/**
 * 测试配置工具类，用于获取测试数据的基础路径
 * 通过系统属性 test.data.base.path 传递基准路径
 */
public class TestConfig {
    
    private static final String TEST_DATA_BASE_PATH_PROPERTY = "test.data.base.path";
    private static final String DEFAULT_TEST_DATA_BASE_PATH = "./tmp";
    
    /**
     * 获取测试数据的基础路径
     * @return 测试数据的基础路径
     */
    public static String getTestDataBasePath() {
        return System.getProperty(TEST_DATA_BASE_PATH_PROPERTY, DEFAULT_TEST_DATA_BASE_PATH);
    }
    
    /**
     * 获取指定测试类型的完整路径
     * @param testType 测试类型（performance, functional, concurrent）
     * @param subPath 子路径
     * @return 完整的测试数据路径
     */
    public static String getTestDataPath(String testType, String subPath) {
        String basePath = getTestDataBasePath();
        if (subPath != null && !subPath.isEmpty()) {
            return basePath + "/" + testType + "/" + subPath;
        } else {
            return basePath + "/" + testType;
        }
    }
    
    /**
     * 获取性能测试数据路径
     * @param subPath 子路径
     * @return 性能测试数据路径
     */
    public static String getPerformanceTestDataPath(String subPath) {
        return getTestDataPath("performance", subPath);
    }
    
    /**
     * 获取功能测试数据路径
     * @param subPath 子路径
     * @return 功能测试数据路径
     */
    public static String getFunctionalTestDataPath(String subPath) {
        return getTestDataPath("functional", subPath);
    }
    
    /**
     * 获取并发测试数据路径
     * @param subPath 子路径
     * @return 并发测试数据路径
     */
    public static String getConcurrentTestDataPath(String subPath) {
        return getTestDataPath("concurrent", subPath);
    }
}