package com.brianxiadong.lsmtree;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 异步批量 I/O 工具类
 * <p>
 * 提供并发执行多个 I/O 任务的能力，并等待所有任务完成。
 * 使用 {@link CompletableFuture#allOf} 来聚合结果。
 */
public final class AsyncIOBatch {
    
    /**
     * 写任务定义
     */
    public static final class WriteTask {
        public final String filename;
        public final long offset;
        public final byte[] data;
        
        public WriteTask(String filename, long offset, byte[] data) {
            this.filename = filename;
            this.offset = offset;
            this.data = data;
        }
    }

    /**
     * 读任务定义
     */
    public static final class ReadTask {
        public final String filename;
        public final long offset;
        public final int length;
        
        public ReadTask(String filename, long offset, int length) {
            this.filename = filename;
            this.offset = offset;
            this.length = length;
        }
    }

    /**
     * 批量执行写操作
     * 
     * @param io AsyncIOManager 实例
     * @param tasks 写任务列表
     * @return 当所有写操作完成时完成的 Future
     * @throws IOException 如果提交任务失败
     */
    public static CompletableFuture<Void> writeMany(AsyncIOManager io, List<WriteTask> tasks) throws IOException {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (WriteTask t : tasks) futures.add(io.writeAsync(t.filename, t.offset, t.data));
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * 批量执行读操作
     * 
     * @param io AsyncIOManager 实例
     * @param tasks 读任务列表
     * @return 包含每个读操作结果 Future 的列表
     * @throws IOException 如果提交任务失败
     */
    public static List<CompletableFuture<byte[]>> readMany(AsyncIOManager io, List<ReadTask> tasks) throws IOException {
        List<CompletableFuture<byte[]>> futures = new ArrayList<>();
        for (ReadTask t : tasks) futures.add(io.readAsync(t.filename, t.offset, t.length));
        return futures;
    }

    /**
     * 批量同步不同文件
     * <p>
     * 对涉及的每个文件仅调用一次 syncAsync，避免重复同步。
     * 
     * @param io AsyncIOManager 实例
     * @param tasks 写任务列表（用于提取涉及的文件名）
     * @return 当所有同步操作完成时完成的 Future
     * @throws IOException 如果提交任务失败
     */
    public static CompletableFuture<Void> syncDistinctFiles(AsyncIOManager io, List<WriteTask> tasks) throws IOException {
        Map<String, List<WriteTask>> byFile = tasks.stream().collect(Collectors.groupingBy(t -> t.filename));
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (String f : byFile.keySet()) futures.add(io.syncAsync(f));
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
}