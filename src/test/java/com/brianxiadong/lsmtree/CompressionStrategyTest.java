package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 压缩策略测试类
 * 测试LZ4压缩的读写功能
 */
public class CompressionStrategyTest {
    @Test
    public void testLZ4RoundTrip() throws Exception {
        TestLogger log = new TestLogger("LZ4压缩读写测试");
        log.start("测试LZ4压缩的完整读写流程");
        
        String dir = Files.createTempDirectory("sst-lz4").toFile().getAbsolutePath();
        String file = dir + "/sstable_level0_" + System.currentTimeMillis() + ".db";
        log.data("临时目录", dir);
        log.data("SSTable文件", file);
        
        log.step("创建100条测试数据");
        List<KeyValue> data = new ArrayList<>();
        for (int i = 0; i < 100; i++) data.add(new KeyValue("k"+i, "v"+i));
        data.sort(KeyValue::compareTo);
        log.data("数据条数", data.size());
        
        log.step("使用LZ4压缩写入SSTable");
        CompressionStrategy lz4 = new LZ4CompressionStrategy();
        SSTable t = new SSTable(file, data, lz4);
        long fileSize = new java.io.File(file).length();
        log.data("压缩后文件大小", fileSize + " bytes");
        
        log.step("验证读取数据正确性");
        int verified = 0;
        for (int i = 0; i < 100; i++) {
            String v = t.get("k"+i);
            Assert.assertEquals("v"+i, v);
            verified++;
        }
        log.data("验证通过条数", verified);
        log.assertSuccess("所有数据读取正确");
        log.pass();
    }
}
