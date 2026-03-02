package com.brianxiadong.lsmtree;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.*;

/**
 * LSM Tree 参数化测试类
 * 使用不同配置组合运行相同的测试用例
 * 
 * 测试不同 MemTable 大小对性能和行为的影响
 */
@RunWith(Parameterized.class)
public class LSMTreeParameterizedTest extends LSMTreeTestBase {
    
    private final int memTableSize;
    private final int dataCount;
    private final String configName;
    
    /**
     * 测试配置参数
     * 每个数组元素是一个测试配置：{memTableSize, dataCount, configName}
     */
    @Parameters(name = "{2} - MemTable={0}, DataCount={1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            { 10, 50, "SmallMemTable" },      // 小 MemTable，频繁刷盘
            { 100, 500, "MediumMemTable" },   // 中等 MemTable
            { 1000, 1000, "LargeMemTable" },  // 大 MemTable，较少刷盘
        });
    }
    
    /**
     * 构造函数接收参数化配置
     */
    public LSMTreeParameterizedTest(int memTableSize, int dataCount, String configName) {
        this.memTableSize = memTableSize;
        this.dataCount = dataCount;
        this.configName = configName;
    }
    
    /**
     * 覆盖默认 MemTable 大小，使用参数化配置
     */
    @Override
    protected int getDefaultMemTableSize() {
        return memTableSize;
    }
    
    /**
     * 测试基本读写操作
     */
    @Test
    public void testBasicOperations() throws IOException {
        logger = new TestLogger(configName + " - 基本操作测试");
        logger.start("测试配置: MemTable=" + memTableSize + ", DataCount=" + dataCount);
        
        logger.step("批量插入 " + dataCount + " 条数据");
        long startTime = System.currentTimeMillis();
        bulkInsert(0, dataCount);
        long insertTime = System.currentTimeMillis() - startTime;
        logger.data("插入耗时", insertTime + "ms");
        
        LSMTree.LSMTreeStats stats = lsmTree.getStats();
        logger.data("SSTable数量", stats.getSsTableCount());
        logger.data("MemTable大小", stats.getActiveMemTableSize());
        
        logger.step("验证所有数据");
        startTime = System.currentTimeMillis();
        verifyDataRange(0, dataCount);
        long verifyTime = System.currentTimeMillis() - startTime;
        logger.data("验证耗时", verifyTime + "ms");
        
        logger.assertSuccess("所有数据验证通过");
        logger.pass();
    }
    
    /**
     * 测试刷盘后数据完整性
     */
    @Test
    public void testFlushAndRecovery() throws IOException {
        logger = new TestLogger(configName + " - 刷盘恢复测试");
        logger.start("测试配置: MemTable=" + memTableSize + ", DataCount=" + dataCount);
        
        logger.step("插入数据");
        bulkInsert(0, dataCount);
        
        logger.step("执行刷盘");
        lsmTree.flush();
        
        LSMTree.LSMTreeStats stats = lsmTree.getStats();
        logger.data("刷盘后SSTable数量", stats.getSsTableCount());
        logger.data("刷盘后MemTable大小", stats.getActiveMemTableSize());
        
        logger.step("验证刷盘后数据完整性");
        verifyDataRange(0, dataCount);
        logger.assertSuccess("刷盘后数据完整");
        
        logger.step("重启后验证数据恢复");
        reopenAndVerify(0, dataCount);
        logger.assertSuccess("重启后数据恢复成功");
        
        logger.pass();
    }
    
    /**
     * 测试更新操作
     */
    @Test
    public void testUpdateOperations() throws IOException {
        logger = new TestLogger(configName + " - 更新操作测试");
        logger.start("测试配置: MemTable=" + memTableSize);
        
        logger.step("插入初始数据");
        bulkInsert(0, Math.min(100, dataCount));
        
        logger.step("更新部分数据");
        int updateCount = Math.min(50, dataCount / 2);
        for (int i = 0; i < updateCount; i++) {
            lsmTree.put("key" + i, "updated_value" + i);
        }
        logger.data("更新条数", updateCount);
        
        logger.step("验证更新结果");
        for (int i = 0; i < updateCount; i++) {
            assertEquals("updated_value" + i, lsmTree.get("key" + i));
        }
        logger.assertSuccess("更新操作正确");
        
        logger.step("刷盘后验证更新");
        lsmTree.flush();
        for (int i = 0; i < updateCount; i++) {
            assertEquals("updated_value" + i, lsmTree.get("key" + i));
        }
        logger.assertSuccess("刷盘后更新仍然正确");
        
        logger.pass();
    }
    
    /**
     * 测试删除操作
     */
    @Test
    public void testDeleteOperations() throws IOException {
        logger = new TestLogger(configName + " - 删除操作测试");
        logger.start("测试配置: MemTable=" + memTableSize);
        
        logger.step("插入测试数据");
        bulkInsert(0, Math.min(100, dataCount));
        
        logger.step("删除部分数据");
        int deleteCount = Math.min(30, dataCount / 3);
        for (int i = 0; i < deleteCount; i++) {
            lsmTree.delete("key" + i);
        }
        logger.data("删除条数", deleteCount);
        
        logger.step("验证删除结果");
        for (int i = 0; i < deleteCount; i++) {
            assertNull(lsmTree.get("key" + i));
        }
        logger.assertSuccess("删除操作正确");
        
        logger.step("验证未删除数据仍然可访问");
        for (int i = deleteCount; i < Math.min(100, dataCount); i++) {
            assertEquals("value" + i, lsmTree.get("key" + i));
        }
        logger.assertSuccess("未删除数据完整");
        
        logger.step("刷盘后验证删除");
        lsmTree.flush();
        for (int i = 0; i < deleteCount; i++) {
            assertNull(lsmTree.get("key" + i));
        }
        logger.assertSuccess("刷盘后删除仍然正确");
        
        logger.pass();
    }
}
