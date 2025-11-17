package com.brianxiadong.lsmtree;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public interface AsyncIOManager {
    CompletableFuture<byte[]> readAsync(String filename, long offset, int length) throws IOException;
    CompletableFuture<Void> writeAsync(String filename, long offset, byte[] data) throws IOException;
    CompletableFuture<Void> syncAsync(String filename) throws IOException;
    void close() throws IOException;
}