package com.brianxiadong.lsmtree;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Write-Ahead Log 实现
 * 确保数据持久性和崩溃恢复
 */
public class WriteAheadLog {
    private final String filePath;
    private BufferedWriter writer;
    private final Object lock = new Object();

    public WriteAheadLog(String filePath) throws IOException {
        this.filePath = filePath;
        this.writer = new BufferedWriter(new FileWriter(filePath, true));
    }

    /**
     * 追加日志条目
     */
    public void append(LogEntry entry) throws IOException {
        synchronized (lock) {
            writer.write(entry.toString());
            writer.newLine();
            writer.flush(); // 确保立即写入磁盘
        }
    }

    /**
     * 检查点操作 - 清理已刷盘的日志
     */
    public void checkpoint() throws IOException {
        synchronized (lock) {
            if (writer != null) {
                writer.close();
            }

            // 创建新的空WAL文件
            File file = new File(filePath);
            if (file.exists()) {
                file.delete();
            }

            // 重新打开writer
            this.writer = new BufferedWriter(new FileWriter(filePath, true));
        }
    }

    /**
     * 从WAL恢复数据
     */
    public List<LogEntry> recover() throws IOException {
        List<LogEntry> entries = new ArrayList<>();
        File file = new File(filePath);

        if (!file.exists()) {
            return entries;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LogEntry entry = LogEntry.fromString(line);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }

        return entries;
    }

    /**
     * 关闭WAL
     */
    public void close() throws IOException {
        synchronized (lock) {
            if (writer != null) {
                writer.close();
            }
        }
    }

    /**
     * WAL日志条目
     */
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
            return String.format("%s|%s|%s|%d",
                    operation, key, value != null ? value : "", timestamp);
        }

        public static LogEntry fromString(String line) {
            if (line == null || line.trim().isEmpty()) {
                return null;
            }

            String[] parts = line.split("\\|", 4);
            if (parts.length < 3) {
                return null;
            }

            try {
                Operation op = Operation.valueOf(parts[0]);
                String key = parts[1];
                String value = parts.length > 2 && !parts[2].isEmpty() ? parts[2] : null;
                long timestamp = parts.length > 3 ? Long.parseLong(parts[3]) : System.currentTimeMillis();

                return new LogEntry(op, key, value, timestamp);
            } catch (Exception e) {
                return null; // 忽略无效的日志条目
            }
        }
    }

    /**
     * WAL操作类型
     */
    public enum Operation {
        PUT, DELETE
    }
}