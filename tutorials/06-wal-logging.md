# 第6章：WAL 写前日志

## 1. 什么是 WAL？

**WAL (Write-Ahead Logging)** 是一种广泛应用于数据库系统中的日志记录技术，用于确保数据的**持久性 (Durability)** 和**原子性 (Atomicity)**。

在 LSM Tree 中，内存表 (MemTable) 是易失的。如果服务器突然断电或进程崩溃，存储在内存中的数据将会丢失。为了解决这个问题，LSM Tree 遵循**"日志先行"**原则：

> **核心原则**: 任何修改操作在应用到 MemTable 之前，必须先持久化到磁盘上的 WAL 文件中。

这样，即使系统崩溃，我们也可以通过重放 (Replay) WAL 文件中的日志来恢复 MemTable 的状态。

---

## 2. WAL 在 LSM Tree 中的作用

WAL 是连接"高性能内存写入"和"数据绝对安全"的桥梁。

```text
写入流程 (The Write Path):
1. 接收写请求 (PUT/DELETE)
2. [关键步骤] 将操作追加写入 WAL 文件 -> 磁盘 fsync
3. 将操作更新到 MemTable -> 内存操作
4. 向客户端返回"写入成功"

恢复流程 (The Recovery Path):
1. 系统启动
2. 检查是否存在 WAL 文件
3. 逐行读取 WAL，将操作重新执行到 MemTable
4. 删除旧的 WAL 文件（因为数据已经恢复到内存了）
5. 开始对外服务
```

**为什么 WAL 快？**
虽然 WAL 也是写磁盘，但它是**顺序追加写 (Sequential Append)**。在机械硬盘上，顺序写的性能可以达到 100MB/s+，而随机写可能只有 1-2MB/s。WAL 巧妙地利用了这一特性。

---

## 3. WAL 文件格式设计

WAL 需要一种简单、紧凑且易于解析的格式。我们采用基于文本的管道分隔格式（实际生产中常用二进制格式如 Protocol Buffers）：

```text
WAL 文件内容示例:
put|user:1001|Alice|1678888888000
put|user:1002|Bob|1678888888001
delete|user:1001||1678888889000
put|config:timeout|5000|1678888890000
...
```

**格式说明**:

- **操作类型**: `put` 或 `delete`
- **键**: 用户键 (String)
- **值**: 用户值 (String，删除操作为空)
- **时间戳**: 确保恢复后的时序正确
- **校验和 (可选)**: 生产环境通常会在每条日志末尾加上 CRC32 校验和，以检测磁盘静默错误 (Bit Rot)。

---

## 4. WAL 实现解析

### 4.1 核心实现

```java
package com.brianxiadong.lsmtree;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Write-Ahead Log 实现
 * 确保数据持久性和崩溃恢复
 */
public class WriteAheadLog {
    private final String filePath;           // WAL 文件路径
    private BufferedWriter writer;           // 缓冲写入器，提高 I/O 性能
    private final Object lock = new Object(); // 写入锁，确保多线程写入的顺序性

    // WAL 构造器
    public WriteAheadLog(String filePath) throws IOException {
        this.filePath = filePath;
        // append = true: 必须使用追加模式，防止覆盖已有日志
        this.writer = new BufferedWriter(new FileWriter(filePath, true));
    }

    /**
     * 追加日志条目
     * 这是写路径中最关键的一步
     */
    public void append(LogEntry entry) throws IOException {
        synchronized (lock) {                // 必须加锁，保证日志顺序与操作顺序一致
            writer.write(entry.toString());  // 写入日志内容
            writer.newLine();                // 换行

            // 关键点：flush()
            // 在严格模式下，这里应该调用 fsync (FileDescriptor.sync())
            // 以确保数据真正落盘，而不仅仅是停留在 OS 的 Page Cache 中
            writer.flush();
        }
    }

    /**
     * 检查点操作 (Checkpoint) - 清理已刷盘的日志
     * 当 MemTable 成功刷写到 SSTable 后，对应的 WAL 就可以删除了
     */
    public void checkpoint() throws IOException {
        synchronized (lock) {
            if (writer != null) {
                writer.close();
            }

            // 安全删除旧文件
            File file = new File(filePath);
            if (file.exists()) {
                file.delete();
            }

            // 创建新的空 WAL 文件准备接收新写入
            this.writer = new BufferedWriter(new FileWriter(filePath, true));
        }
    }
}
```

**核心设计解析**：

- **同步写入**: `append` 方法必须是同步的。如果两个线程并发写入，日志内容可能会交错（Garbled），导致无法恢复。
- **Flush 策略**: 代码中使用了 `writer.flush()`。在高性能场景下，可能会采用"批处理刷盘"（Group Commit）策略，即每隔几毫秒或积累一定数据量后刷盘一次，但这会牺牲少量的数据持久性（可能丢失最近几毫秒的数据）。

### 4.2 恢复机制和日志条目

恢复过程本质上是一个**重放 (Replay)** 过程。

```java
    /**
     * 从 WAL 恢复数据
     * 系统启动时调用
     */
    public List<LogEntry> recover() throws IOException {
        List<LogEntry> entries = new ArrayList<>();
        File file = new File(filePath);

        if (!file.exists()) {
            return entries;                           // 新系统，无日志
        }

        // 使用 try-with-resources 自动关闭文件流
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 容错处理：如果某一行损坏，是停止恢复还是跳过？
                // 这里选择跳过损坏行，尽可能恢复更多数据
                LogEntry entry = LogEntry.fromString(line);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }

        return entries;
    }

    /**
     * WAL 日志条目封装
     */
    public static class LogEntry {
        private final Operation operation;
        private final String key;
        private final String value;
        private final long timestamp;

        // 私有构造函数
        private LogEntry(Operation operation, String key, String value, long timestamp) {
            this.operation = operation;
            this.key = key;
            this.value = value;
            this.timestamp = timestamp;
        }

        // 工厂方法
        public static LogEntry put(String key, String value) {
            return new LogEntry(Operation.PUT, key, value, System.currentTimeMillis());
        }

        public static LogEntry delete(String key) {
            return new LogEntry(Operation.DELETE, key, null, System.currentTimeMillis());
        }

        // 序列化：转为文本格式
        @Override
        public String toString() {
            return String.format("%s|%s|%s|%d",
                    operation, key, value != null ? value : "", timestamp);
        }

        // 反序列化：从文本解析
        public static LogEntry fromString(String line) {
            if (line == null || line.trim().isEmpty()) {
                return null;
            }

            String[] parts = line.split("\\|", 4);
            if (parts.length < 3) {
                return null;                          // 格式错误
            }

            try {
                Operation op = Operation.valueOf(parts[0]);
                String key = parts[1];
                String value = parts.length > 2 && !parts[2].isEmpty() ? parts[2] : null;
                long timestamp = parts.length > 3 ? Long.parseLong(parts[3]) : System.currentTimeMillis();

                return new LogEntry(op, key, value, timestamp);
            } catch (Exception e) {
                // 记录日志：发现损坏的 WAL 条目
                return null;
            }
        }
    }

    public enum Operation {
        PUT, DELETE
    }
```

---

## 5. 小结

WAL 是 LSM Tree 乃至所有持久化存储系统的"安全底座"。

1. **持久性**: 它是数据落盘的第一站。
2. **性能**: 它将随机写转化为顺序写，掩盖了磁盘 I/O 的延迟。
3. **正确性**: 它保证了操作的原子性和时序性。

---

## 6. 思考题

1. **刷盘策略**: 每次写入都 `fsync` 会导致性能下降，如何优化？（提示：Group Commit）
2. **日志截断**: 如果 WAL 文件无限增长怎么办？什么时候可以安全地截断或删除它？
3. **损坏恢复**: 如果 WAL 文件的最后一行只写了一半（系统突然断电），恢复程序应该如何处理？

**下一章预告**: 随着数据不断写入，SSTable 文件越来越多，如何管理这些文件？我们将深入 LSM Tree 的心脏——压缩策略。
