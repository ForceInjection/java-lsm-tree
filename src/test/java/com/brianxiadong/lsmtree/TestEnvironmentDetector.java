package com.brianxiadong.lsmtree;

/**
 * 测试环境检测工具类
 * 用于调试测试环境检测逻辑
 */
public class TestEnvironmentDetector {
    public static void main(String[] args) {
        System.out.println("=== 测试环境检测 ===");
        System.out.println("surefire.test.class.path: " + System.getProperty("surefire.test.class.path"));
        System.out.println("test: " + System.getProperty("test"));
        System.out.println("Current thread name: " + Thread.currentThread().getName());
        System.out.println("sun.java.command: " + System.getProperty("sun.java.command"));
        
        // 更全面的检测方法
        boolean isTestEnv = isRunningInTestEnvironment();
        System.out.println("检测结果 - 是否为测试环境: " + isTestEnv);
    }
    
    public static boolean isRunningInTestEnvironment() {
        return System.getProperty("surefire.test.class.path") != null ||
               System.getProperty("test") != null ||
               Thread.currentThread().getName().contains("surefire") ||
               System.getProperty("sun.java.command", "").contains("surefire");
    }
}