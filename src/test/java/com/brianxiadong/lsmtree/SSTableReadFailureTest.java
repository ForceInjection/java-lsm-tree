package com.brianxiadong.lsmtree;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * SSTable读取失败测试类
 * 测试SSTable读取无效文件时的异常处理
 */
public class SSTableReadFailureTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test(expected = IOException.class)
    public void testInvalidHeaderThrows() throws Exception {
        TestLogger log = new TestLogger("SSTable无效文件头测试");
        log.start("测试读取无效文件头的SSTable抛出异常");
        
        log.step("创建无效的SSTable文件");
        File f = tmp.newFile("bad.db");
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(f))) {
            out.writeBytes("LSM1");
            out.writeBytes("LZ");
        }
        log.data("文件内容", "LSM1 + LZ（无效头部）");
        log.data("期望结果", "IOException");
        
        log.step("尝试读取无效文件");
        new SSTable(f.getAbsolutePath());
        log.pass();
    }
}

