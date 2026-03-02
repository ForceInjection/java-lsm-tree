package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * AsyncIOBatch 功能测试
 */
public class AsyncIOBatchTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testBatchWriteAndRead() throws IOException, ExecutionException, InterruptedException {
        File file1 = tempFolder.newFile("batch1.dat");
        File file2 = tempFolder.newFile("batch2.dat");
        
        try (AsyncIOManager io = new NioAsyncIOManager(4, "test_batch")) {
            List<AsyncIOBatch.WriteTask> writeTasks = new ArrayList<>();
            byte[] data1 = "Hello".getBytes(StandardCharsets.UTF_8);
            byte[] data2 = "World".getBytes(StandardCharsets.UTF_8);
            
            writeTasks.add(new AsyncIOBatch.WriteTask(file1.getAbsolutePath(), 0, data1));
            writeTasks.add(new AsyncIOBatch.WriteTask(file2.getAbsolutePath(), 0, data2));
            
            // 批量写入
            CompletableFuture<Void> writeFuture = AsyncIOBatch.writeMany(io, writeTasks);
            writeFuture.get();
            
            // 批量同步
            CompletableFuture<Void> syncFuture = AsyncIOBatch.syncDistinctFiles(io, writeTasks);
            syncFuture.get();
            
            // 批量读取
            List<AsyncIOBatch.ReadTask> readTasks = new ArrayList<>();
            readTasks.add(new AsyncIOBatch.ReadTask(file1.getAbsolutePath(), 0, data1.length));
            readTasks.add(new AsyncIOBatch.ReadTask(file2.getAbsolutePath(), 0, data2.length));
            
            List<CompletableFuture<byte[]>> readFutures = AsyncIOBatch.readMany(io, readTasks);
            
            // 验证结果
            byte[] res1 = readFutures.get(0).get();
            byte[] res2 = readFutures.get(1).get();
            
            Assert.assertArrayEquals(data1, res1);
            Assert.assertArrayEquals(data2, res2);
        }
    }
}
