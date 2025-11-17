package com.brianxiadong.lsmtree;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class AsyncIOBatch {
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

    public static CompletableFuture<Void> writeMany(AsyncIOManager io, List<WriteTask> tasks) throws IOException {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (WriteTask t : tasks) futures.add(io.writeAsync(t.filename, t.offset, t.data));
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    public static List<CompletableFuture<byte[]>> readMany(AsyncIOManager io, List<ReadTask> tasks) throws IOException {
        List<CompletableFuture<byte[]>> futures = new ArrayList<>();
        for (ReadTask t : tasks) futures.add(io.readAsync(t.filename, t.offset, t.length));
        return futures;
    }

    public static CompletableFuture<Void> syncDistinctFiles(AsyncIOManager io, List<WriteTask> tasks) throws IOException {
        Map<String, List<WriteTask>> byFile = tasks.stream().collect(Collectors.groupingBy(t -> t.filename));
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (String f : byFile.keySet()) futures.add(io.syncAsync(f));
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
}