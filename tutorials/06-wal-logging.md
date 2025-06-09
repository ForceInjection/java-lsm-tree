🔥 推荐一个高质量的Java LSM Tree开源项目！
[https://github.com/brianxiadong/java-lsm-tree](https://github.com/brianxiadong/java-lsm-tree)
**java-lsm-tree** 是一个从零实现的Log-Structured Merge Tree，专为高并发写入场景设计。
核心亮点：
⚡ 极致性能：写入速度超过40万ops/秒，完爆传统B+树
🏗️ 完整架构：MemTable跳表 + SSTable + WAL + 布隆过滤器 + 多级压缩
📚 深度教程：12章详细教程，从基础概念到生产优化，每行代码都有注释
🔒 并发安全：读写锁机制，支持高并发场景
💾 数据可靠：WAL写前日志确保崩溃恢复，零数据丢失
适合谁？
- 想深入理解LSM Tree原理的开发者
- 需要高写入性能存储引擎的项目
- 准备数据库/存储系统面试的同学
- 对分布式存储感兴趣的工程师
⭐ 给个Star支持开源！

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
import java.util.ArrayList;
import java.util.List;

/**
 * Write-Ahead Log 实现
 * 确保数据持久性和崩溃恢复
 */
public class WriteAheadLog {
    private final String filePath;           // WAL文件路径
    private BufferedWriter writer;           // 缓冲写入器，提高I/O性能
    private final Object lock = new Object(); // 写入锁，确保线程安全
    
    // WAL构造器，创建或追加WAL文件
    public WriteAheadLog(String filePath) throws IOException {
        this.filePath = filePath;            // 保存文件路径
        // 使用追加模式(true)打开文件，确保现有数据不被覆盖
        this.writer = new BufferedWriter(new FileWriter(filePath, true));
    }
    
    /**
     * 追加日志条目
     */
    public void append(LogEntry entry) throws IOException {
        synchronized (lock) {                // 同步块确保多线程安全
            writer.write(entry.toString());  // 写入日志条目内容
            writer.newLine();                // 添加换行符分隔条目
            writer.flush();                  // 立即刷盘，确保持久性
        }
    }
    
    /**
     * 检查点操作 - 清理已刷盘的日志
     */
    public void checkpoint() throws IOException {
        synchronized (lock) {
            if (writer != null) {
                writer.close();              // 关闭当前写入器
            }

            // 创建新的空WAL文件
            File file = new File(filePath);
            if (file.exists()) {
                file.delete();               // 删除现有文件
            }

            // 重新打开writer
            this.writer = new BufferedWriter(new FileWriter(filePath, true));
        }
    }
}
```

**核心设计解析**：这个WAL实现采用了几个关键的设计决策。首先使用`BufferedWriter`提高I/O性能，同时在每次写入后立即调用`flush()`确保数据持久化到磁盘。`synchronized`关键字保证了多线程环境下写入操作的原子性。追加模式（append=true）确保即使程序重启，现有的WAL记录也不会丢失。这种设计在性能和可靠性之间取得了良好的平衡。

### 恢复机制和日志条目

```java
    /**
     * 从WAL恢复数据
     */
    public List<LogEntry> recover() throws IOException {
        List<LogEntry> entries = new ArrayList<>();    // 存储恢复的日志条目
        File file = new File(filePath);               // 创建文件对象

        if (!file.exists()) {
            return entries;                           // 没有WAL文件，返回空列表
        }

        // 使用try-with-resources确保文件正确关闭
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;                              // 当前读取的行
            while ((line = reader.readLine()) != null) { // 逐行读取WAL文件
                LogEntry entry = LogEntry.fromString(line); // 解析日志条目
                if (entry != null) {                  // 解析成功的条目
                    entries.add(entry);               // 添加到恢复数据列表
                }
            }
        }

        return entries;                               // 返回所有恢复的日志条目
    }

    /**
     * 关闭WAL
     */
    public void close() throws IOException {
        synchronized (lock) {
            if (writer != null) {
                writer.close();                       // 关闭写入器
            }
        }
    }

    /**
     * WAL日志条目
     */
    public static class LogEntry {
        private final Operation operation;            // 操作类型
        private final String key;                     // 键
        private final String value;                   // 值
        private final long timestamp;                 // 时间戳

        // 私有构造函数
        private LogEntry(Operation operation, String key, String value, long timestamp) {
            this.operation = operation;
            this.key = key;
            this.value = value;
            this.timestamp = timestamp;
        }

        // 创建PUT操作的日志条目
        public static LogEntry put(String key, String value) {
            return new LogEntry(Operation.PUT, key, value, System.currentTimeMillis());
        }

        // 创建DELETE操作的日志条目
        public static LogEntry delete(String key) {
            return new LogEntry(Operation.DELETE, key, null, System.currentTimeMillis());
        }

        // Getter方法
        public Operation getOperation() { return operation; }
        public String getKey() { return key; }
        public String getValue() { return value; }
        public long getTimestamp() { return timestamp; }

        // 序列化为字符串
        @Override
        public String toString() {
            return String.format("%s|%s|%s|%d",
                    operation, key, value != null ? value : "", timestamp);
        }

        // 从字符串反序列化
        public static LogEntry fromString(String line) {
            if (line == null || line.trim().isEmpty()) {
                return null;                          // 空行跳过
            }

            String[] parts = line.split("\\|", 4);   // 按|分隔符拆分
            if (parts.length < 3) {
                return null;                          // 格式错误，跳过此条目
            }

            try {
                Operation op = Operation.valueOf(parts[0]); // 解析操作类型
                String key = parts[1];                // 键
                String value = parts.length > 2 && !parts[2].isEmpty() ? parts[2] : null; // 值
                long timestamp = parts.length > 3 ? Long.parseLong(parts[3]) : System.currentTimeMillis(); // 时间戳

                return new LogEntry(op, key, value, timestamp);
            } catch (Exception e) {
                return null;                          // 解析失败，忽略无效的日志条目
            }
        }
    }

    /**
     * WAL操作类型
     */
    public enum Operation {
        PUT, DELETE
    }
```

**恢复机制解析**：恢复过程是WAL的核心功能，它将磁盘上的日志记录重新加载到内存中。这个实现采用了流式读取方式，逐行解析日志文件，避免了一次性加载整个文件带来的内存压力。解析器对每个日志条目进行严格的格式验证，确保只有有效的条目才会被恢复。对于格式错误的条目，采用跳过策略而不是抛出异常，这提高了系统的容错能力。时间戳的保留确保了恢复后的数据保持原有的时序关系。


## 小结

WAL是LSM Tree数据持久性的关键保障：

1. **故障恢复**: 确保数据不丢失
2. **顺序写入**: 利用磁盘性能特性
3. **原子性**: 保证操作的原子性
4. **可扩展**: 支持压缩、异步、分布式等特性

---

## 思考题

1. 为什么WAL必须在MemTable写入之前完成？
2. 如何平衡WAL的性能和可靠性？
3. 在什么情况下需要压缩WAL？

**下一章预告**: 我们将深入学习LSM Tree的压缩策略、多级合并和性能优化。 