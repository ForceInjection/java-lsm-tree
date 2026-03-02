package com.brianxiadong.lsmtree;

import org.junit.Test;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

/**
 * 调试测试
 * 验证SSTable和LSMTree的基本读写与恢复功能
 * 
 * 使用 LSMTreeTestBase 提供的统一生命周期管理和工具方法
 */
public class DebugTest extends LSMTreeTestBase {
    
    /**
     * 使用小容量 MemTable 以便快速测试
     */
    @Override
    protected int getDefaultMemTableSize() {
        return 10;
    }

    @Test
    public void testSSTableWriteAndRead() throws IOException {
        TestLogger log = new TestLogger("SSTable读写测试");
        log.start("测试SSTable的写入和读取功能");
        
        // 在临时目录中创建测试文件
        File testFile = new File(testDataDir, "debug_sstable.db");
        log.data("测试文件", testFile.getAbsolutePath());

        log.step("创建测试数据");
        List<KeyValue> data = Arrays.asList(
                new KeyValue("key1", "value1"),
                new KeyValue("key2", "value2"));
        log.data("数据条数", data.size());

        log.step("写入SSTable");
        SSTable writeTable = new SSTable(testFile.getAbsolutePath(), data);

        log.step("直接从写入的SSTable读取验证");
        assertEquals("value1", writeTable.get("key1"));
        assertEquals("value2", writeTable.get("key2"));
        log.assertSuccess("写入后读取验证成功");

        log.step("从文件加载新的SSTable");
        SSTable readTable = new SSTable(testFile.getAbsolutePath());

        log.step("测试从文件加载后的读取");
        assertEquals("value1", readTable.get("key1"));
        assertEquals("value2", readTable.get("key2"));
        log.assertSuccess("文件加载后读取验证成功");
        
        log.pass();
    }

    @Test
    public void testLSMTreeRecoverySimple() throws IOException {
        TestLogger log = new TestLogger("LSMTree恢复测试");
        log.start("测试LSMTree的数据持久化和恢复功能");

        log.step("第一阶段：写入数据并刷盘");
        lsmTree.put("key1", "value1");
        log.data("写入数据", "key1=value1");
        lsmTree.flush();
        log.step("执行flush刷盘");
        lsmTree.close();
        lsmTree = null; // 标记为已关闭
        log.step("关闭LSMTree实例");

        log.step("检查SSTable文件是否创建");
        File[] files = testDataDir.listFiles((d, name) -> name.endsWith(".db"));
        int fileCount = files != null ? files.length : 0;
        log.data("SSTable文件数量", fileCount);
        if (files != null) {
            for (File f : files) {
                log.data("文件", f.getName() + " (size: " + f.length() + " bytes)");
            }
        }

        log.step("第二阶段：重新打开并读取");
        lsmTree = createLSMTree();
        String result = lsmTree.get("key1");
        log.data("恢复读取结果", "key1=" + result);
        assertEquals("value1", result);
        log.assertSuccess("数据恢复验证成功");
        
        log.pass();
    }
}
