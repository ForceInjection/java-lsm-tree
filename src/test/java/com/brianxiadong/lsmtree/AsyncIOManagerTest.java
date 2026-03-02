package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * AsyncIOManager 测试类
 * 测试异步读写和同步功能
 */
public class AsyncIOManagerTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testReadWriteAsync() throws IOException, ExecutionException, InterruptedException {
        TestLogger log = new TestLogger("AsyncIO 读写测试");
        log.start("测试 NioAsyncIOManager 的异步读写能力");

        File file = tempFolder.newFile("async_test.dat");
        String filePath = file.getAbsolutePath();
        
        try (AsyncIOManager ioManager = new NioAsyncIOManager(2, "test")) {
            String content = "Hello, Async IO World!";
            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            
            log.step("异步写入数据");
            CompletableFuture<Void> writeFuture = ioManager.writeAsync(filePath, 0, data);
            writeFuture.get(); // 等待完成
            log.pass();
            
            log.step("异步读取数据");
            CompletableFuture<byte[]> readFuture = ioManager.readAsync(filePath, 0, data.length);
            byte[] readData = readFuture.get();
            String readContent = new String(readData, StandardCharsets.UTF_8);
            
            log.data("写入内容", content);
            log.data("读取内容", readContent);
            
            Assert.assertEquals(content, readContent);
            log.assertSuccess("读写内容一致");
            
            log.step("测试 Sync");
            CompletableFuture<Void> syncFuture = ioManager.syncAsync(filePath);
            syncFuture.get();
            log.pass();
        }
    }
    
    @Test
    public void testLargeWriteRecursive() throws IOException, ExecutionException, InterruptedException {
        TestLogger log = new TestLogger("AsyncIO 大数据写入测试");
        log.start("测试递归写入逻辑");
        
        File file = tempFolder.newFile("large_async_test.dat");
        String filePath = file.getAbsolutePath();
        
        // 创建较大的数据，尝试触发分段写入（虽然 AsynchronousFileChannel 可能一次写完，但验证逻辑正确性）
        int size = 1024 * 1024; // 1MB
        byte[] largeData = new byte[size];
        for (int i = 0; i < size; i++) {
            largeData[i] = (byte) (i % 128);
        }
        
        try (AsyncIOManager ioManager = new NioAsyncIOManager(2, "test_large")) {
            log.step("写入 1MB 数据");
            CompletableFuture<Void> writeFuture = ioManager.writeAsync(filePath, 0, largeData);
            writeFuture.get();
            log.pass();
            
            log.step("读取验证");
            CompletableFuture<byte[]> readFuture = ioManager.readAsync(filePath, 0, size);
            byte[] readData = readFuture.get();
            
            Assert.assertEquals(size, readData.length);
            for (int i = 0; i < size; i++) {
                if (largeData[i] != readData[i]) {
                    Assert.fail("数据不一致 at index " + i);
                }
            }
            log.assertSuccess("大数据读写一致");
        }
    }
}
