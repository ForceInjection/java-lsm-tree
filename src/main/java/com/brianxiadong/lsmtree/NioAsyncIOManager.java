package com.brianxiadong.lsmtree;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 NIO AsynchronousFileChannel 的异步 I/O 管理器
 * <p>
 * 提供非阻塞的文件读写操作，并集成了 Micrometer 指标监控。
 * 使用自定义的 ThreadPoolExecutor 来处理 I/O 回调。
 */
public class NioAsyncIOManager implements AsyncIOManager {
    private final ExecutorService ioExecutor;
    private final ConcurrentHashMap<String, AsynchronousFileChannel> channelCache = new ConcurrentHashMap<>();

    private final Timer readTimer;
    private final Timer writeTimer;
    private final Counter readBytes;
    private final Counter writeBytes;
    private final AtomicLong inflight = new AtomicLong(0);

    public NioAsyncIOManager(int threads, String metricsName) throws IOException {
        this.ioExecutor = new ThreadPoolExecutor(
                threads,
                threads,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(threads * 1024),
                r -> {
                    Thread t = new Thread(r, "LSM-IO-" + System.nanoTime());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
        

        MeterRegistry registry = MetricsRegistry.get();
        this.readTimer = Timer.builder("lsm.io.async.read.latency").tag("name", metricsName).register(registry);
        this.writeTimer = Timer.builder("lsm.io.async.write.latency").tag("name", metricsName).register(registry);
        this.readBytes = Counter.builder("lsm.io.async.read.bytes").tag("name", metricsName).register(registry);
        this.writeBytes = Counter.builder("lsm.io.async.write.bytes").tag("name", metricsName).register(registry);
        Gauge.builder("lsm.io.async.inflight", inflight, AtomicLong::get).tag("name", metricsName).register(registry);
    }

    private AsynchronousFileChannel openChannel(String filename) throws IOException {
        AsynchronousFileChannel ch = channelCache.get(filename);
        if (ch != null && ch.isOpen()) return ch;
        Path path = Paths.get(filename);
        AsynchronousFileChannel created = AsynchronousFileChannel.open(
                path,
                EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE),
                ioExecutor);
        AsynchronousFileChannel prev = channelCache.put(filename, created);
        if (prev != null && prev.isOpen()) {
            try { prev.close(); } catch (IOException ignored) {}
        }
        return created;
    }

    @Override
    public CompletableFuture<byte[]> readAsync(String filename, long offset, int length) throws IOException {
        Objects.requireNonNull(filename, "filename");
        if (offset < 0 || length < 0) throw new IllegalArgumentException("offset/length must be >= 0");
        AsynchronousFileChannel ch = openChannel(filename);
        ByteBuffer buf = ByteBuffer.allocate(length);
        long start = System.nanoTime();
        inflight.incrementAndGet();
        CompletableFuture<byte[]> cf = new CompletableFuture<>();
        ch.read(buf, offset, null, new CompletionHandlerImpl<>(bytesRead -> {
            inflight.decrementAndGet();
            readTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            if (bytesRead < 0) {
                cf.completeExceptionally(new IOException("EOF"));
                return;
            }
            readBytes.increment(bytesRead);
            // 兼容 Java 8: 显式转换为 Buffer 以避免 NoSuchMethodError
            ((java.nio.Buffer) buf).flip();
            byte[] out = new byte[buf.remaining()];
            buf.get(out);
            cf.complete(out);
        }, ex -> {
            inflight.decrementAndGet();
            cf.completeExceptionally(ex);
        }));
        return cf;
    }

    @Override
    public CompletableFuture<Void> writeAsync(String filename, long offset, byte[] data) throws IOException {
        Objects.requireNonNull(filename, "filename");
        Objects.requireNonNull(data, "data");
        if (offset < 0) throw new IllegalArgumentException("offset must be >= 0");
        AsynchronousFileChannel ch = openChannel(filename);
        ByteBuffer buf = ByteBuffer.wrap(data);
        long start = System.nanoTime();
        inflight.incrementAndGet();
        CompletableFuture<Void> cf = new CompletableFuture<>();
        writeRecursive(ch, offset, buf, cf, start);
        return cf;
    }

    private void writeRecursive(AsynchronousFileChannel ch, long pos, ByteBuffer buf, CompletableFuture<Void> cf, long start) {
        ch.write(buf, pos, null, new CompletionHandlerImpl<>(written -> {
            if (written > 0) {
                writeBytes.increment(written);
            }
            if (buf.hasRemaining()) {
                writeRecursive(ch, pos + written, buf, cf, start);
            } else {
                inflight.decrementAndGet();
                writeTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
                cf.complete(null);
            }
        }, ex -> {
            inflight.decrementAndGet();
            cf.completeExceptionally(ex);
        }));
    }

    @Override
    public CompletableFuture<Void> syncAsync(String filename) throws IOException {
        Objects.requireNonNull(filename, "filename");
        CompletableFuture<Void> cf = new CompletableFuture<>();
        inflight.incrementAndGet();
        long start = System.nanoTime();
        
        // 使用 cached channel 进行 force，而不是打开新的 channel
        // 打开新 channel 并 force 可能无法刷新 AsynchronousFileChannel 写入的数据
        try {
            AsynchronousFileChannel ch = openChannel(filename);
            // AsynchronousFileChannel.force 是同步方法，但在 AsyncIOManager 中应该异步执行吗？
            // force 可能会阻塞，所以提交到线程池执行是正确的
            ioExecutor.submit(() -> {
                try {
                    ch.force(true);
                    writeTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
                    cf.complete(null);
                } catch (IOException ex) {
                    cf.completeExceptionally(ex);
                } finally {
                    inflight.decrementAndGet();
                }
            });
        } catch (IOException e) {
            inflight.decrementAndGet();
            cf.completeExceptionally(e);
        }
        return cf;
    }

    @Override
    public void close() throws IOException {
        // 先关闭所有 channel，这会取消所有待处理的 I/O 操作
        for (AsynchronousFileChannel ch : channelCache.values()) {
            try { 
                ch.close(); 
            } catch (IOException ignored) {}
        }
        channelCache.clear();
        
        // 然后关闭线程池
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow();
                // 再等待一小段时间让中断生效
                if (!ioExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    System.err.println("警告: 部分I/O线程未能在超时内终止");
                }
            }
        } catch (InterruptedException e) {
            ioExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    static final class CompletionHandlerImpl<V> implements java.nio.channels.CompletionHandler<V, Object> {
        private final java.util.function.Consumer<V> onSuccess;
        private final java.util.function.Consumer<Throwable> onError;
        CompletionHandlerImpl(java.util.function.Consumer<V> onSuccess, java.util.function.Consumer<Throwable> onError) {
            this.onSuccess = onSuccess;
            this.onError = onError;
        }
        @Override public void completed(V result, Object attachment) { onSuccess.accept(result); }
        @Override public void failed(Throwable exc, Object attachment) { onError.accept(exc); }
    }
}