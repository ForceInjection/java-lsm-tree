package com.brianxiadong.lsmtree.tools;

import com.brianxiadong.lsmtree.KeyValue;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 工具模块功能测试
 * 验证 SSTableAnalyzer 的正确性
 */
public class ToolsFunctionTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testSSTableAnalyzer() throws IOException {
        File sstableFile = tempFolder.newFile("test.db");
        
        // 手动创建一个简单的 SSTable 文件
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(sstableFile))) {
            // Entry count
            dos.writeInt(2);
            
            // Entry 1: key="k1", deleted=false, value="v1", timestamp=100
            dos.writeUTF("k1");
            dos.writeBoolean(false);
            dos.writeUTF("v1");
            dos.writeLong(100L);
            
            // Entry 2: key="k2", deleted=true, timestamp=200
            dos.writeUTF("k2");
            dos.writeBoolean(true); // deleted
            // dos.writeUTF("v2"); // 删除条目没有 value，Analyzer 逻辑: if (!deleted) readUTF
            dos.writeLong(200L);
        }
        
        // 分析文件
        SSTableAnalyzer.AnalysisResult result = SSTableAnalyzer.analyzeFile(sstableFile.getAbsolutePath());
        
        Assert.assertTrue("文件应有效: " + result.getErrorMessage(), result.isValid());
        Assert.assertEquals("总条目数应为 2", 2, result.getEntryCount());
        Assert.assertEquals("活跃条目应为 1", 1, result.getActiveCount());
        Assert.assertEquals("删除条目应为 1", 1, result.getDeletedCount());
        
        List<KeyValue> entries = result.getEntries();
        Assert.assertEquals(2, entries.size());
        
        // 验证顺序，entries 按写入顺序读取，但 Analyzer 会检查排序
        // SSTableAnalyzer: entries.add(...)
        // 验证数据是否按键排序: isDataOrdered
        // k1 < k2，顺序正确
        
        KeyValue kv1 = entries.get(0);
        Assert.assertEquals("k1", kv1.getKey());
        Assert.assertEquals("v1", kv1.getValue());
        Assert.assertFalse(kv1.isDeleted());
        
        KeyValue kv2 = entries.get(1);
        Assert.assertEquals("k2", kv2.getKey());
        Assert.assertTrue(kv2.isDeleted());
    }
    
    @Test
    public void testSSTableAnalyzerInvalidFile() throws IOException {
        File invalidFile = tempFolder.newFile("invalid.db");
        try (FileOutputStream fos = new FileOutputStream(invalidFile)) {
            fos.write("invalid content".getBytes(StandardCharsets.UTF_8));
        }
        
        SSTableAnalyzer.AnalysisResult result = SSTableAnalyzer.analyzeFile(invalidFile.getAbsolutePath());
        Assert.assertFalse("文件应被标记为无效", result.isValid());
        Assert.assertNotNull("应包含错误信息", result.getErrorMessage());
    }
}
