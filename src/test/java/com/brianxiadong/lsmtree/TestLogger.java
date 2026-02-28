package com.brianxiadong.lsmtree;

/**
 * 测试日志输出工具类
 * 提供统一的测试输出格式，帮助用户获得足够的测试信息
 */
public class TestLogger {
    private final String testName;
    private long startTime;
    
    public TestLogger(String testName) {
        this.testName = testName;
    }
    
    /**
     * 开始测试，打印测试名称和描述
     */
    public void start(String description) {
        System.out.println("\n=== " + testName + " ===");
        System.out.println("描述: " + description);
        startTime = System.currentTimeMillis();
    }
    
    /**
     * 记录测试步骤
     */
    public void step(String message) {
        System.out.println("  → " + message);
    }
    
    /**
     * 记录关键数据
     */
    public void data(String label, Object value) {
        System.out.println("  📊 " + label + ": " + value);
    }
    
    /**
     * 记录断言成功
     */
    public void assertSuccess(String message) {
        System.out.println("  ✅ " + message);
    }
    
    /**
     * 记录断言失败（用于预期失败的场景）
     */
    public void assertExpectedFailure(String message) {
        System.out.println("  ⚠️ " + message);
    }
    
    /**
     * 结束测试，打印耗时和结果
     */
    public void pass() {
        long duration = System.currentTimeMillis() - startTime;
        System.out.println("✅ 测试通过 (" + duration + "ms)");
    }
    
    /**
     * 结束测试，打印耗时和跳过原因
     */
    public void skip(String reason) {
        long duration = System.currentTimeMillis() - startTime;
        System.out.println("⏭️ 测试跳过: " + reason + " (" + duration + "ms)");
    }
    
    /**
     * 打印分隔线
     */
    public static void separator() {
        System.out.println("----------------------------------------");
    }
    
    /**
     * 打印测试类开始信息
     */
    public static void classStart(String className, String description) {
        System.out.println("\n╔═══════════════════════════════════════════════════════════════");
        System.out.println("║ 测试类: " + className);
        System.out.println("║ " + description);
        System.out.println("╚═══════════════════════════════════════════════════════════════");
    }
    
    /**
     * 打印测试类结束信息
     */
    public static void classEnd(String className, int passed, int failed) {
        System.out.println("\n╔═══════════════════════════════════════════════════════════════");
        System.out.println("║ " + className + " 测试完成");
        System.out.println("║ 通过: " + passed + " | 失败: " + failed);
        System.out.println("╚═══════════════════════════════════════════════════════════════");
    }
}
