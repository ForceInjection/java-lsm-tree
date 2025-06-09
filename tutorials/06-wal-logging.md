# 第6章：WAL 写前日志

## 什么是WAL？

**WAL (Write-Ahead Logging)** 是一种确保数据持久性的日志记录技术。在LSM Tree中，WAL的作用是：

- **故障恢复**: 系统崩溃后能够恢复MemTable中的数据
- **数据持久性**: 保证写入的数据不会因为系统故障而丢失  
- **原子性**: 确保写操作的原子性
- **顺序写入**: 利用磁盘顺序写入的高性能

## WAL在LSM Tree中的作用

```
写入流程:
1. 写入WAL日志 (磁盘顺序写)
2. 写入MemTable (内存写)
3. 返回成功给客户端

恢复流程:
1. 读取WAL日志文件
2. 重放所有操作到MemTable
3. 删除已恢复的WAL文件
```

**关键原则**: 只有WAL写入成功后，才能写入MemTable！

## WAL文件格式设计

我们采用简单高效的文本格式：

```
WAL文件格式:
put|key1|value1|timestamp
put|key2|value2|timestamp  
delete|key3||timestamp
put|key4|value4|timestamp
...
```

**格式说明**:
- **操作类型**: `put` 或 `delete`
- **键**: 用户键
- **值**: 用户值（删除操作为空）
- **时间戳**: 操作时间戳
- **分隔符**: 使用 `|` 分隔字段

## WAL实现解析

### 核心实现

```java
package com.brianxiadong.lsmtree;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class WriteAheadLog {
    private String logFilePath;
    private BufferedWriter writer;
    private final Object writeLock = new Object();
    
    public WriteAheadLog(String logFilePath) throws IOException {
        this.logFilePath = logFilePath;
        this.writer = new BufferedWriter(new FileWriter(logFilePath, true));
    }
    
    // 记录写入操作
    public void logPut(String key, String value, long timestamp) throws IOException {
        String logEntry = String.format("put|%s|%s|%d", key, value, timestamp);
        writeLogEntry(logEntry);
    }
    
    // 记录删除操作
    public void logDelete(String key, long timestamp) throws IOException {
        String logEntry = String.format("delete|%s||%d", key, timestamp);
        writeLogEntry(logEntry);
    }
    
    private void writeLogEntry(String logEntry) throws IOException {
        synchronized (writeLock) {
            writer.write(logEntry);
            writer.newLine();
            writer.flush(); // 立即刷盘
        }
    }
}
```

### 恢复机制

```java
// 从WAL文件恢复数据
public static List<KeyValue> recoverFromWAL(String logFilePath) throws IOException {
    List<KeyValue> recoveredData = new ArrayList<>();
    
    File logFile = new File(logFilePath);
    if (!logFile.exists()) {
        return recoveredData; // 没有WAL文件，返回空列表
    }
    
    try (BufferedReader reader = new BufferedReader(
            new FileReader(logFilePath, StandardCharsets.UTF_8))) {
        
        String line;
        while ((line = reader.readLine()) != null) {
            KeyValue kv = parseLogEntry(line);
            if (kv != null) {
                recoveredData.add(kv);
            }
        }
    }
    
    return recoveredData;
}

private static KeyValue parseLogEntry(String logEntry) {
    String[] parts = logEntry.split("\\|");
    if (parts.length != 4) {
        return null; // 格式错误，跳过
    }
    
    String operation = parts[0];
    String key = parts[1];
    String value = parts[2];
    long timestamp = Long.parseLong(parts[3]);
    
    if ("put".equals(operation)) {
        return new KeyValue(key, value, timestamp);
    } else if ("delete".equals(operation)) {
        return new KeyValue(key, null, timestamp, true);
    }
    
    return null;
}
```

### 检查点机制

```java
// 检查点：清理已刷盘的WAL条目
public void checkpoint() throws IOException {
    synchronized (writeLock) {
        // 关闭当前writer
        if (writer != null) {
            writer.close();
        }
        
        // 创建新的WAL文件
        String newLogPath = logFilePath + ".new";
        writer = new BufferedWriter(new FileWriter(newLogPath));
        
        // 删除旧文件，重命名新文件
        File oldFile = new File(logFilePath);
        File newFile = new File(newLogPath);
        
        if (oldFile.delete()) {
            if (newFile.renameTo(oldFile)) {
                // 重新创建writer指向新文件
                this.writer = new BufferedWriter(new FileWriter(logFilePath, true));
            }
        }
    }
}
```

## 高级特性

### 1. 批量写入优化

```java
public class BatchedWAL extends WriteAheadLog {
    private final List<String> batch = new ArrayList<>();
    private final int batchSize;
    private volatile boolean flushInProgress = false;
    
    public BatchedWAL(String logFilePath, int batchSize) throws IOException {
        super(logFilePath);
        this.batchSize = batchSize;
    }
    
    @Override
    protected void writeLogEntry(String logEntry) throws IOException {
        synchronized (writeLock) {
            batch.add(logEntry);
            
            if (batch.size() >= batchSize || flushInProgress) {
                flushBatch();
            }
        }
    }
    
    private void flushBatch() throws IOException {
        if (batch.isEmpty()) return;
        
        flushInProgress = true;
        try {
            for (String entry : batch) {
                writer.write(entry);
                writer.newLine();
            }
            writer.flush();
            batch.clear();
        } finally {
            flushInProgress = false;
        }
    }
    
    // 强制刷新所有待处理的条目
    public void forceFlush() throws IOException {
        synchronized (writeLock) {
            flushBatch();
        }
    }
}
```

### 2. 压缩WAL

```java
public class CompressedWAL extends WriteAheadLog {
    private GZIPOutputStream gzipOut;
    
    public CompressedWAL(String logFilePath) throws IOException {
        super(logFilePath);
        setupCompression();
    }
    
    private void setupCompression() throws IOException {
        FileOutputStream fos = new FileOutputStream(logFilePath, true);
        this.gzipOut = new GZIPOutputStream(fos);
        this.writer = new BufferedWriter(new OutputStreamWriter(gzipOut, StandardCharsets.UTF_8));
    }
    
    @Override
    protected void writeLogEntry(String logEntry) throws IOException {
        synchronized (writeLock) {
            writer.write(logEntry);
            writer.newLine();
            writer.flush();
            gzipOut.flush(); // 确保压缩数据写入
        }
    }
    
    // 恢复压缩的WAL文件
    public static List<KeyValue> recoverFromCompressedWAL(String logFilePath) throws IOException {
        List<KeyValue> recoveredData = new ArrayList<>();
        
        try (GZIPInputStream gzipIn = new GZIPInputStream(new FileInputStream(logFilePath));
             BufferedReader reader = new BufferedReader(new InputStreamReader(gzipIn, StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                KeyValue kv = parseLogEntry(line);
                if (kv != null) {
                    recoveredData.add(kv);
                }
            }
        }
        
        return recoveredData;
    }
}
```

### 3. 循环WAL

```java
public class CircularWAL {
    private final String[] logFiles;
    private final int maxFileSize;
    private int currentFileIndex = 0;
    private WriteAheadLog currentWAL;
    
    public CircularWAL(String basePath, int fileCount, int maxFileSize) throws IOException {
        this.logFiles = new String[fileCount];
        this.maxFileSize = maxFileSize;
        
        for (int i = 0; i < fileCount; i++) {
            logFiles[i] = basePath + "_" + i + ".wal";
        }
        
        this.currentWAL = new WriteAheadLog(logFiles[0]);
    }
    
    public void logPut(String key, String value, long timestamp) throws IOException {
        checkFileRotation();
        currentWAL.logPut(key, value, timestamp);
    }
    
    private void checkFileRotation() throws IOException {
        File currentFile = new File(logFiles[currentFileIndex]);
        
        if (currentFile.length() > maxFileSize) {
            rotateToNextFile();
        }
    }
    
    private void rotateToNextFile() throws IOException {
        currentWAL.close();
        
        currentFileIndex = (currentFileIndex + 1) % logFiles.length;
        
        // 清空下一个文件（如果存在）
        File nextFile = new File(logFiles[currentFileIndex]);
        if (nextFile.exists()) {
            nextFile.delete();
        }
        
        currentWAL = new WriteAheadLog(logFiles[currentFileIndex]);
    }
    
    public List<KeyValue> recoverAll() throws IOException {
        List<KeyValue> allData = new ArrayList<>();
        
        // 从当前文件的下一个开始恢复（最旧的文件）
        int startIndex = (currentFileIndex + 1) % logFiles.length;
        
        for (int i = 0; i < logFiles.length; i++) {
            int fileIndex = (startIndex + i) % logFiles.length;
            String filePath = logFiles[fileIndex];
            
            if (new File(filePath).exists()) {
                List<KeyValue> fileData = WriteAheadLog.recoverFromWAL(filePath);
                allData.addAll(fileData);
            }
        }
        
        return allData;
    }
}
```

## 性能优化

### 1. 异步写入

```java
public class AsyncWAL extends WriteAheadLog {
    private final ExecutorService writeExecutor;
    private final BlockingQueue<LogEntry> writeQueue;
    private volatile boolean running = true;
    
    private static class LogEntry {
        final String operation;
        final String key;
        final String value;
        final long timestamp;
        final CompletableFuture<Void> future;
        
        LogEntry(String operation, String key, String value, long timestamp) {
            this.operation = operation;
            this.key = key;
            this.value = value;
            this.timestamp = timestamp;
            this.future = new CompletableFuture<>();
        }
    }
    
    public AsyncWAL(String logFilePath) throws IOException {
        super(logFilePath);
        this.writeQueue = new LinkedBlockingQueue<>();
        this.writeExecutor = Executors.newSingleThreadExecutor();
        
        // 启动后台写入线程
        writeExecutor.submit(this::processWrites);
    }
    
    public CompletableFuture<Void> logPutAsync(String key, String value, long timestamp) {
        LogEntry entry = new LogEntry("put", key, value, timestamp);
        
        try {
            writeQueue.put(entry);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            entry.future.completeExceptionally(e);
        }
        
        return entry.future;
    }
    
    private void processWrites() {
        while (running || !writeQueue.isEmpty()) {
            try {
                LogEntry entry = writeQueue.poll(100, TimeUnit.MILLISECONDS);
                if (entry != null) {
                    try {
                        if ("put".equals(entry.operation)) {
                            super.logPut(entry.key, entry.value, entry.timestamp);
                        } else if ("delete".equals(entry.operation)) {
                            super.logDelete(entry.key, entry.timestamp);
                        }
                        entry.future.complete(null);
                    } catch (Exception e) {
                        entry.future.completeExceptionally(e);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    @Override
    public void close() throws IOException {
        running = false;
        writeExecutor.shutdown();
        
        try {
            if (!writeExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                writeExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            writeExecutor.shutdownNow();
        }
        
        super.close();
    }
}
```

### 2. 内存映射文件

```java
public class MemoryMappedWAL {
    private final RandomAccessFile file;
    private final FileChannel channel;
    private MappedByteBuffer buffer;
    private final int fileSize;
    private int position = 0;
    
    public MemoryMappedWAL(String filePath, int fileSize) throws IOException {
        this.fileSize = fileSize;
        this.file = new RandomAccessFile(filePath, "rw");
        this.channel = file.getChannel();
        
        // 预分配文件大小
        file.setLength(fileSize);
        this.buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);
    }
    
    public synchronized void logPut(String key, String value, long timestamp) throws IOException {
        String logEntry = String.format("put|%s|%s|%d\n", key, value, timestamp);
        byte[] bytes = logEntry.getBytes(StandardCharsets.UTF_8);
        
        if (position + bytes.length > fileSize) {
            throw new IOException("WAL文件已满");
        }
        
        buffer.position(position);
        buffer.put(bytes);
        buffer.force(); // 强制写入磁盘
        
        position += bytes.length;
    }
    
    public void close() throws IOException {
        if (buffer != null) {
            buffer.force();
        }
        if (channel != null) {
            channel.close();
        }
        if (file != null) {
            file.close();
        }
    }
}
```

## 故障恢复策略

### 1. 完整恢复

```java
public class WALRecoveryManager {
    
    public static LSMTree recoverLSMTree(String dataDirectory, String walPath) throws IOException {
        LSMTree lsmTree = new LSMTree(dataDirectory);
        
        // 1. 恢复WAL中的数据
        List<KeyValue> walData = WriteAheadLog.recoverFromWAL(walPath);
        System.out.printf("从WAL恢复了 %d 条记录%n", walData.size());
        
        // 2. 重放操作到MemTable
        for (KeyValue kv : walData) {
            if (kv.isDeleted()) {
                lsmTree.delete(kv.getKey());
            } else {
                lsmTree.put(kv.getKey(), kv.getValue());
            }
        }
        
        // 3. 清理WAL文件
        File walFile = new File(walPath);
        if (walFile.exists()) {
            walFile.delete();
            System.out.println("WAL文件已清理");
        }
        
        return lsmTree;
    }
}
```

### 2. 增量恢复

```java
public class IncrementalRecovery {
    
    public static void recoverFromCheckpoint(LSMTree lsmTree, String checkpointPath, String walPath) throws IOException {
        // 1. 加载检查点
        loadCheckpoint(lsmTree, checkpointPath);
        
        // 2. 重放检查点之后的WAL
        List<KeyValue> incrementalData = WriteAheadLog.recoverFromWAL(walPath);
        
        for (KeyValue kv : incrementalData) {
            if (kv.isDeleted()) {
                lsmTree.delete(kv.getKey());
            } else {
                lsmTree.put(kv.getKey(), kv.getValue());
            }
        }
        
        System.out.printf("增量恢复了 %d 条记录%n", incrementalData.size());
    }
    
    private static void loadCheckpoint(LSMTree lsmTree, String checkpointPath) throws IOException {
        // 从检查点文件加载已持久化的状态
        File checkpointFile = new File(checkpointPath);
        if (!checkpointFile.exists()) {
            return;
        }
        
        // 这里应该加载SSTable文件和其他持久化状态
        System.out.println("检查点已加载");
    }
}
```

### 3. 损坏恢复

```java
public class CorruptionRecovery {
    
    public static List<KeyValue> recoverWithValidation(String walPath) throws IOException {
        List<KeyValue> validData = new ArrayList<>();
        int corruptedLines = 0;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(walPath))) {
            String line;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                
                try {
                    KeyValue kv = parseAndValidateLogEntry(line);
                    if (kv != null) {
                        validData.add(kv);
                    }
                } catch (Exception e) {
                    corruptedLines++;
                    System.err.printf("第%d行损坏，跳过: %s%n", lineNumber, line);
                }
            }
        }
        
        System.out.printf("恢复完成: %d条有效记录, %d条损坏记录%n", validData.size(), corruptedLines);
        return validData;
    }
    
    private static KeyValue parseAndValidateLogEntry(String logEntry) throws Exception {
        String[] parts = logEntry.split("\\|");
        if (parts.length != 4) {
            throw new IllegalArgumentException("格式错误");
        }
        
        String operation = parts[0];
        String key = parts[1];
        String value = parts[2];
        long timestamp;
        
        try {
            timestamp = Long.parseLong(parts[3]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("时间戳格式错误");
        }
        
        // 验证操作类型
        if (!"put".equals(operation) && !"delete".equals(operation)) {
            throw new IllegalArgumentException("未知操作类型: " + operation);
        }
        
        // 验证键不为空
        if (key.isEmpty()) {
            throw new IllegalArgumentException("键不能为空");
        }
        
        if ("put".equals(operation)) {
            return new KeyValue(key, value, timestamp);
        } else {
            return new KeyValue(key, null, timestamp, true);
        }
    }
}
```

## 监控和调试

### 1. WAL统计信息

```java
public class WALMetrics {
    private final AtomicLong writeCount = new AtomicLong(0);
    private final AtomicLong writeTime = new AtomicLong(0);
    private final AtomicLong flushCount = new AtomicLong(0);
    private final AtomicLong fileSize = new AtomicLong(0);
    
    public void recordWrite(long duration) {
        writeCount.incrementAndGet();
        writeTime.addAndGet(duration);
    }
    
    public void recordFlush() {
        flushCount.incrementAndGet();
    }
    
    public void updateFileSize(long size) {
        fileSize.set(size);
    }
    
    public String getStats() {
        long writes = writeCount.get();
        double avgWriteTime = writes > 0 ? (double) writeTime.get() / writes : 0.0;
        
        return String.format(
            "WAL统计: 写入%,d次, 平均%.2fμs, 刷盘%,d次, 文件大小%,d字节",
            writes, avgWriteTime / 1000.0, flushCount.get(), fileSize.get()
        );
    }
}
```

### 2. WAL分析工具

```java
public class WALAnalyzer {
    
    public static void analyzeWAL(String walPath) throws IOException {
        Map<String, Integer> operationCounts = new HashMap<>();
        Set<String> uniqueKeys = new HashSet<>();
        long minTimestamp = Long.MAX_VALUE;
        long maxTimestamp = Long.MIN_VALUE;
        int totalEntries = 0;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(walPath))) {
            String line;
            
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length == 4) {
                    String operation = parts[0];
                    String key = parts[1];
                    long timestamp = Long.parseLong(parts[3]);
                    
                    operationCounts.merge(operation, 1, Integer::sum);
                    uniqueKeys.add(key);
                    minTimestamp = Math.min(minTimestamp, timestamp);
                    maxTimestamp = Math.max(maxTimestamp, timestamp);
                    totalEntries++;
                }
            }
        }
        
        System.out.println("=== WAL分析报告 ===");
        System.out.printf("总条目数: %,d%n", totalEntries);
        System.out.printf("唯一键数: %,d%n", uniqueKeys.size());
        System.out.printf("时间范围: %s - %s%n", 
                         new Date(minTimestamp), new Date(maxTimestamp));
        
        System.out.println("操作分布:");
        operationCounts.forEach((op, count) -> 
            System.out.printf("  %s: %,d (%.1f%%)%n", 
                            op, count, 100.0 * count / totalEntries));
    }
}
```

## 实际应用场景

### 1. 高可用系统

```java
public class HighAvailabilityWAL {
    private final WriteAheadLog primaryWAL;
    private final WriteAheadLog replicaWAL;
    
    public HighAvailabilityWAL(String primaryPath, String replicaPath) throws IOException {
        this.primaryWAL = new WriteAheadLog(primaryPath);
        this.replicaWAL = new WriteAheadLog(replicaPath);
    }
    
    public void logPut(String key, String value, long timestamp) throws IOException {
        // 同时写入主WAL和备份WAL
        IOException primaryException = null;
        IOException replicaException = null;
        
        try {
            primaryWAL.logPut(key, value, timestamp);
        } catch (IOException e) {
            primaryException = e;
        }
        
        try {
            replicaWAL.logPut(key, value, timestamp);
        } catch (IOException e) {
            replicaException = e;
        }
        
        // 至少有一个成功就认为写入成功
        if (primaryException != null && replicaException != null) {
            throw new IOException("主WAL和备份WAL都写入失败");
        }
    }
}
```

### 2. 分布式WAL

```java
public class DistributedWAL {
    private final List<WriteAheadLog> replicaWALs;
    private final int requiredReplicas;
    
    public DistributedWAL(List<String> replicaPaths, int requiredReplicas) throws IOException {
        this.requiredReplicas = requiredReplicas;
        this.replicaWALs = new ArrayList<>();
        
        for (String path : replicaPaths) {
            replicaWALs.add(new WriteAheadLog(path));
        }
    }
    
    public void logPut(String key, String value, long timestamp) throws IOException {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        // 并行写入所有副本
        for (WriteAheadLog wal : replicaWALs) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    wal.logPut(key, value, timestamp);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            futures.add(future);
        }
        
        // 等待足够数量的副本写入成功
        int successCount = 0;
        for (CompletableFuture<Void> future : futures) {
            try {
                future.get(1, TimeUnit.SECONDS);
                successCount++;
            } catch (Exception e) {
                // 忽略失败的副本
            }
        }
        
        if (successCount < requiredReplicas) {
            throw new IOException("成功写入的副本数不足");
        }
    }
}
```

## 小结

WAL是LSM Tree数据持久性的关键保障：

1. **故障恢复**: 确保数据不丢失
2. **顺序写入**: 利用磁盘性能特性
3. **原子性**: 保证操作的原子性
4. **可扩展**: 支持压缩、异步、分布式等特性

## 下一步学习

现在你已经理解了WAL的设计原理，接下来我们将学习压缩策略：

继续阅读：[第7章：压缩策略](07-compaction-strategy.md)

---

## 思考题

1. 为什么WAL必须在MemTable写入之前完成？
2. 如何平衡WAL的性能和可靠性？
3. 在什么情况下需要压缩WAL？

**下一章预告**: 我们将深入学习LSM Tree的压缩策略、多级合并和性能优化。 