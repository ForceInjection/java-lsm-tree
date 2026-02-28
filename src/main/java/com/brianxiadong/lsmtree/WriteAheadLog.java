package com.brianxiadong.lsmtree;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Write-Ahead Log 实现
 * 使用同步 I/O 避免创建额外的异步线程
 */
public class WriteAheadLog {
    private final String filePath;
    private FileChannel channel;
    private final Object lock = new Object();
    private final AtomicLong position = new AtomicLong(0L);
    private final int syncBatchSize;
    private final long syncIntervalMillis;
    private long lastSyncTimeMillis;

    public WriteAheadLog(String filePath) throws IOException {
        this(filePath, 1, 0L);
    }

    public WriteAheadLog(String filePath, int syncBatchSize, long syncIntervalMillis) throws IOException {
        this.filePath = filePath;
        this.syncBatchSize = Math.max(1, syncBatchSize);
        this.syncIntervalMillis = Math.max(0L, syncIntervalMillis);
        this.lastSyncTimeMillis = System.currentTimeMillis();
        File file = new File(filePath);
        long initial = file.exists() ? file.length() : 0L;
        this.position.set(initial);
        // 使用同步 FileChannel 替代 AsynchronousFileChannel
        this.channel = FileChannel.open(Paths.get(filePath), 
                StandardOpenOption.WRITE, StandardOpenOption.READ, StandardOpenOption.CREATE);
    }

    public void append(LogEntry entry) throws IOException {
        synchronized (lock) {
            byte[] bytes = (entry.toString() + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ByteBuffer buf = ByteBuffer.wrap(bytes);
            long pos = position.getAndAdd(bytes.length);
            // 使用同步写入
            channel.write(buf, pos);

            boolean needSyncByBatch = position.get() % syncBatchSize == 0;
            boolean needSyncByTime = syncIntervalMillis > 0 && (System.currentTimeMillis() - lastSyncTimeMillis) >= syncIntervalMillis;

            if (needSyncByBatch || needSyncByTime) {
                channel.force(false);
                lastSyncTimeMillis = System.currentTimeMillis();
            }
        }
    }

    public void checkpoint() throws IOException {
        synchronized (lock) {
            if (channel != null && channel.isOpen())
                channel.close();
            File file = new File(filePath);
            if (file.exists())
                file.delete();
            this.channel = FileChannel.open(Paths.get(filePath), 
                    StandardOpenOption.WRITE, StandardOpenOption.READ, StandardOpenOption.CREATE);
            this.position.set(0L);
            this.lastSyncTimeMillis = System.currentTimeMillis();
        }
    }

    public List<LogEntry> recover() throws IOException {
        List<LogEntry> entries = new ArrayList<>();
        File file = new File(filePath);
        if (!file.exists())
            return entries;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LogEntry entry = LogEntry.fromString(line);
                if (entry != null)
                    entries.add(entry);
            }
        }
        return entries;
    }

    public void close() throws IOException {
        synchronized (lock) {
            // 强制刷新未写入的数据
            if (channel != null && channel.isOpen()) {
                channel.force(true);
                channel.close();
            }
        }
    }

    public long sizeBytes() {
        File file = new File(filePath);
        return file.exists() ? file.length() : 0L;
    }

    public static class LogEntry {
        private final Operation operation;
        private final String key;
        private final String value;
        private final long timestamp;

        private LogEntry(Operation operation, String key, String value, long timestamp) {
            this.operation = operation;
            this.key = key;
            this.value = value;
            this.timestamp = timestamp;
        }

        public static LogEntry put(String key, String value) {
            return new LogEntry(Operation.PUT, key, value, System.currentTimeMillis());
        }

        public static LogEntry delete(String key) {
            return new LogEntry(Operation.DELETE, key, null, System.currentTimeMillis());
        }

        public Operation getOperation() {
            return operation;
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return String.format("%s|%s|%s|%d", operation, key, value != null ? value : "", timestamp);
        }

        public static LogEntry fromString(String line) {
            if (line == null || line.trim().isEmpty())
                return null;
            String[] parts = line.split("\\|", 4);
            if (parts.length < 3)
                return null;
            try {
                Operation op = Operation.valueOf(parts[0]);
                String key = parts[1];
                String value = parts.length > 2 && !parts[2].isEmpty() ? parts[2] : null;
                long timestamp = parts.length > 3 ? Long.parseLong(parts[3]) : System.currentTimeMillis();
                return new LogEntry(op, key, value, timestamp);
            } catch (Exception e) {
                return null;
            }
        }
    }

    public enum Operation {
        PUT, DELETE
    }
}
