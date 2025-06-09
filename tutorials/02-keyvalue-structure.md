# 第2章：KeyValue 数据结构

## 为什么需要KeyValue结构？

在LSM Tree中，我们不能简单地存储原始的键值对，因为需要处理以下问题：
- **时间版本控制**: 同一个键可能有多个版本
- **删除语义**: 如何表示删除操作
- **数据完整性**: 确保数据的一致性

因此，我们设计了`KeyValue`类作为LSM Tree的基础数据单元。

## KeyValue 类设计

让我们看看实际的实现：

```java
package com.brianxiadong.lsmtree;

public class KeyValue {
    // 键字段：存储数据的唯一标识符，不可变
    private final String key;        
    // 值字段：存储实际数据内容，删除时为null
    private final String value;      
    // 时间戳：记录数据写入的时间，用于版本控制
    private final long timestamp;    
    // 删除标记：标识该记录是否为删除操作（墓碑）
    private final boolean deleted;   

    // 构造函数：创建正常的键值对
    public KeyValue(String key, String value) {
        this.key = key;                                // 设置键
        this.value = value;                            // 设置值
        this.timestamp = System.currentTimeMillis();  // 使用当前时间作为时间戳
        this.deleted = false;                          // 标记为非删除状态
    }
    
    // 私有构造函数：用于内部创建特定状态的KeyValue对象
    private KeyValue(String key, String value, long timestamp, boolean deleted) {
        this.key = key;              // 设置键
        this.value = value;          // 设置值（可能为null）
        this.timestamp = timestamp;  // 设置指定的时间戳
        this.deleted = deleted;      // 设置删除标记
    }
    
    // 静态工厂方法：创建删除标记（墓碑）
    public static KeyValue createTombstone(String key) {
        // 创建一个删除标记，值为null，标记为已删除
        return new KeyValue(key, null, System.currentTimeMillis(), true);
    }
    
    // 获取键的方法
    public String getKey() {
        return key;
    }
    
    // 获取值的方法
    public String getValue() {
        return value;
    }
    
    // 获取时间戳的方法
    public long getTimestamp() {
        return timestamp;
    }
    
    // 检查是否为删除标记的方法
    public boolean isDeleted() {
        return deleted;
    }
}
```

**代码解释**: 这个KeyValue类是LSM Tree的核心数据结构。它使用`final`关键字确保对象的不可变性，这在多线程环境下非常重要。时间戳字段让我们能够处理同一键的多个版本，而删除标记则实现了LSM Tree特有的"逻辑删除"机制。

## 核心概念详解

### 1. 时间戳 (Timestamp)

**作用**: 记录数据写入的时间，用于版本控制和冲突解决。

```java
// 时间戳的使用场景
KeyValue kv1 = new KeyValue("user:1", "Alice");  // 创建第一个版本，timestamp: 1640995200000
Thread.sleep(1);                                  // 等待1毫秒确保时间戳不同
KeyValue kv2 = new KeyValue("user:1", "Bob");     // 创建第二个版本，timestamp: 1640995200001

// 查询时，较新的时间戳优先
assert kv2.getTimestamp() > kv1.getTimestamp();   // 验证时间戳顺序
```

**代码解释**: 这段代码展示了时间戳的重要作用。当同一个键有多个版本时，时间戳帮助我们确定哪个版本是最新的。通过`Thread.sleep(1)`确保两个操作有不同的时间戳，这在实际应用中体现了LSM Tree的版本控制机制。

**为什么使用时间戳？**
- **版本控制**: 同一键的多个版本按时间排序
- **冲突解决**: 压缩时保留最新版本
- **调试追踪**: 便于问题排查和数据审计

### 2. 删除标记 (Tombstone)

在LSM Tree中，删除操作不是立即物理删除，而是插入一个"墓碑"标记。

```java
// 正常的键值对
KeyValue normalKV = new KeyValue("user:1", "Alice");  // 创建正常键值对
assert !normalKV.isDeleted();                         // 验证不是删除标记
assert normalKV.getValue().equals("Alice");           // 验证值正确

// 删除标记（墓碑）
KeyValue tombstone = KeyValue.createTombstone("user:1");  // 创建删除标记
assert tombstone.isDeleted();                             // 验证是删除标记
assert tombstone.getValue() == null;                      // 验证值为null
```

**代码解释**: 这个例子清楚地展示了正常键值对和删除标记的区别。删除标记本质上是一个特殊的KeyValue对象，它的值为null，删除标记为true。这种设计允许LSM Tree在不修改已存在文件的情况下处理删除操作。

**为什么使用删除标记？**

1. **LSM特性**: 不能就地修改已写入的SSTable文件
2. **性能考虑**: 避免立即的磁盘随机I/O
3. **一致性**: 确保删除操作的持久性和可见性

**删除标记的生命周期**:
```
1. 用户调用delete("key")
     ↓
2. 插入tombstone到MemTable
     ↓ 
3. 刷盘到SSTable（包含tombstone）
     ↓
4. 压缩时清理过期tombstone
     ↓
5. 物理删除完成
```

### 3. 不可变性 (Immutability)

KeyValue对象是不可变的，所有字段都是`final`。

**不可变性的好处**:
- **线程安全**: 多线程环境下安全共享
- **缓存友好**: 可以安全缓存引用
- **语义清晰**: 避免意外修改

```java
// 创建后不能修改
KeyValue kv = new KeyValue("key", "value");  // 创建不可变对象
// kv.key = "newKey";  // 编译错误：final字段不能修改
```

**代码解释**: 由于所有字段都声明为`final`，KeyValue对象一旦创建就无法修改。这种设计在多线程环境下特别有用，因为多个线程可以安全地读取同一个KeyValue对象，而不用担心数据竞争问题。

## 实际应用示例

### 1. 基本CRUD操作

```java
public class KeyValueExample {
    public static void main(String[] args) {
        // 创建数据：构造一个新的键值对
        KeyValue create = new KeyValue("user:1001", "Alice");
        System.out.printf("创建: %s = %s (时间: %d)%n", 
                         create.getKey(),          // 获取键
                         create.getValue(),        // 获取值
                         create.getTimestamp());   // 获取时间戳
        
        // 更新数据：创建同一键的新版本（LSM Tree不支持就地更新）
        KeyValue update = new KeyValue("user:1001", "Alice Smith");
        System.out.printf("更新: %s = %s (时间: %d)%n", 
                         update.getKey(),          // 键保持不变
                         update.getValue(),        // 新的值
                         update.getTimestamp());   // 新的时间戳
        
        // 删除数据：创建墓碑标记而不是物理删除
        KeyValue delete = KeyValue.createTombstone("user:1001");
        System.out.printf("删除: %s (删除标记: %b, 时间: %d)%n", 
                         delete.getKey(),          // 键保持不变
                         delete.isDeleted(),       // 删除标记为true
                         delete.getTimestamp());   // 删除操作的时间戳
    }
}
```

**代码解释**: 这个示例展示了LSM Tree中的基本操作模式。注意"更新"操作实际上是创建一个新的KeyValue对象，而不是修改现有对象。删除操作创建一个特殊的墓碑记录。每个操作都有独特的时间戳，这使得系统能够追踪数据的完整历史。

**输出**:
```
创建: user:1001 = Alice (时间: 1640995200000)
更新: user:1001 = Alice Smith (时间: 1640995200001)  
删除: user:1001 (删除标记: true, 时间: 1640995200002)
```

### 2. 版本控制示例

```java
public class VersionControlExample {
    public static void main(String[] args) throws InterruptedException {
        List<KeyValue> versions = new ArrayList<>();  // 存储同一键的多个版本
        
        // 模拟同一键的多个版本
        versions.add(new KeyValue("config", "v1.0"));      // 添加第一个版本
        Thread.sleep(10);                                   // 确保时间戳不同
        versions.add(new KeyValue("config", "v1.1"));      // 添加第二个版本
        Thread.sleep(10);                                   // 再次确保时间戳不同
        versions.add(KeyValue.createTombstone("config"));  // 添加删除标记
        
        // 按时间戳排序，找到最新版本（时间戳越大越新）
        versions.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        
        KeyValue latest = versions.get(0);  // 获取时间戳最大（最新）的版本
        if (latest.isDeleted()) {           // 检查最新版本是否为删除标记
            System.out.println("键 'config' 已被删除");
        } else {
            System.out.println("最新值: " + latest.getValue());  // 输出最新的值
        }
    }
}
```

**代码解释**: 这个例子模拟了LSM Tree中同一键的多个版本共存的情况。通过对时间戳进行排序，我们可以找到最新的版本。这种机制让LSM Tree能够处理高并发写入，同时保持数据的一致性。注意我们使用`Thread.sleep(10)`来确保每个版本有不同的时间戳。

### 3. 压缩场景模拟

```java
public class CompactionExample {
    // 压缩多个KeyValue记录，只保留每个键的最新版本
    public static List<KeyValue> compactKeyValues(List<KeyValue> input) {
        // 按键分组：将相同键的所有版本放在一起
        Map<String, List<KeyValue>> grouped = input.stream()
            .collect(Collectors.groupingBy(KeyValue::getKey));
        
        List<KeyValue> result = new ArrayList<>();  // 存储压缩后的结果
        
        // 对每个键，只保留最新的版本
        for (Map.Entry<String, List<KeyValue>> entry : grouped.entrySet()) {
            List<KeyValue> versions = entry.getValue();  // 获取该键的所有版本
            
            // 按时间戳倒序排序（最新的在前面）
            versions.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
            
            KeyValue latest = versions.get(0);  // 获取最新版本
            
            // 如果最新版本不是删除标记，则保留它
            if (!latest.isDeleted()) {
                result.add(latest);  // 添加到结果中
            }
            // 删除标记：在压缩时可以彻底清除，不添加到结果中
        }
        
        return result;  // 返回压缩后的结果
    }
    
    public static void main(String[] args) {
        // 创建测试数据：包含多个版本和删除标记
        List<KeyValue> input = Arrays.asList(
            new KeyValue("user:1", "v1"),              // user:1的第一个版本
            new KeyValue("user:1", "v2"),              // user:1的第二个版本（更新）
            new KeyValue("user:2", "Alice"),           // user:2的值
            KeyValue.createTombstone("user:2"),        // user:2的删除标记
            new KeyValue("user:3", "Bob")              // user:3的值
        );
        
        // 执行压缩操作
        List<KeyValue> compacted = compactKeyValues(input);
        
        // 输出压缩前后的记录数量
        System.out.println("压缩前: " + input.size() + " 条记录");
        System.out.println("压缩后: " + compacted.size() + " 条记录");
        
        // 输出压缩后保留的记录
        compacted.forEach(kv -> 
            System.out.println(kv.getKey() + " = " + kv.getValue())
        );
    }
}
```

**代码解释**: 这个压缩示例展示了LSM Tree中的关键操作。压缩过程会遍历所有的KeyValue记录，对于每个键只保留最新的版本。如果最新版本是删除标记，那么该键的所有历史版本都会被清除。这个过程有效地减少了存储空间并提高了查询性能。

**输出**:
```
压缩前: 5 条记录
压缩后: 2 条记录
user:1 = v2
user:3 = Bob
```

## 设计考虑

### 1. 内存效率

KeyValue对象需要频繁创建，我们优化了内存使用：

```java
// 字符串池化（如果键有重复模式）
private static final Map<String, String> KEY_POOL = new ConcurrentHashMap<>();  // 线程安全的键池

// 对重复的键进行池化，减少内存占用
public static String internKey(String key) {
    // 如果键已存在于池中，返回池中的实例；否则添加新键
    return KEY_POOL.computeIfAbsent(key, k -> k);
}
```

**代码解释**: 当系统中有大量相似的键（如"user:1", "user:2"等）时，字符串池化可以显著减少内存占用。`computeIfAbsent`方法确保线程安全，同时避免重复的字符串对象。

### 2. 序列化考虑

为了持久化到SSTable，KeyValue需要支持序列化：

```java
// 序列化格式：key|value|timestamp|deleted
public String serialize() {
    return String.format("%s|%s|%d|%b", 
                        key,                                    // 键
                        value != null ? value : "",             // 值（null时用空字符串表示）
                        timestamp,                              // 时间戳
                        deleted);                               // 删除标记
}

// 反序列化：从字符串恢复KeyValue对象
public static KeyValue deserialize(String line) {
    String[] parts = line.split("\\|");                       // 按分隔符分割
    String key = parts[0];                                     // 解析键
    String value = parts[1].isEmpty() ? null : parts[1];       // 解析值（空字符串转为null）
    long timestamp = Long.parseLong(parts[2]);                 // 解析时间戳
    boolean deleted = Boolean.parseBoolean(parts[3]);          // 解析删除标记
    
    // 使用私有构造函数创建KeyValue对象
    return new KeyValue(key, value, timestamp, deleted);
}
```

**代码解释**: 序列化方法将KeyValue对象转换为可以存储到磁盘的字符串格式。我们使用管道符(|)作为分隔符，并特别处理null值。反序列化过程则是逆向操作，从字符串重建KeyValue对象。这种简单的文本格式便于调试和人工检查。

### 3. 比较和排序

为了支持有序存储，KeyValue实现了比较逻辑：

```java
// 实现Comparable接口，支持自然排序
public int compareTo(KeyValue other) {
    // 首先按键进行字典序比较
    int keyCompare = this.key.compareTo(other.key);
    if (keyCompare != 0) {
        return keyCompare;  // 键不同时，直接返回键的比较结果
    }
    // 键相同时，按时间戳倒序排序（较新的排在前面）
    return Long.compare(other.timestamp, this.timestamp);
}
```

**代码解释**: 这个比较方法首先按键进行排序，确保相同键的记录聚集在一起。当键相同时，按时间戳倒序排列，这样在查找时可以快速找到最新版本。这种排序策略是LSM Tree高效查询的基础。

## 性能影响

### 1. 对象创建开销

每次写入都会创建新的KeyValue对象：

```java
// 测试对象创建性能
public void benchmarkKeyValueCreation() {
    long start = System.nanoTime();  // 记录开始时间（纳秒精度）
    
    // 循环创建10万个KeyValue对象
    for (int i = 0; i < 100_000; i++) {
        KeyValue kv = new KeyValue("key" + i, "value" + i);  // 创建新对象
    }
    
    long duration = System.nanoTime() - start;  // 计算耗时
    System.out.printf("创建10万个KeyValue: %.2f ms%n", duration / 1_000_000.0);
}
```

**代码解释**: 这个基准测试衡量KeyValue对象的创建开销。由于LSM Tree需要频繁创建KeyValue对象，了解这个开销对性能优化很重要。使用纳秒计时器可以得到更精确的测量结果。

**典型结果**: 创建10万个KeyValue约需要10-20ms，对整体性能影响很小。

### 2. 内存占用

```java
// 估算KeyValue内存占用
public static long estimateMemoryUsage(String key, String value) {
    // 对象头：12字节（开启压缩OOP的64位JVM）
    long objectHeader = 12;
    // 4个字段引用：每个引用4字节（压缩OOP）
    long fieldReferences = 4 * 4;  // key, value, timestamp, deleted的引用
    // long timestamp：8字节
    long timestampSize = 8;
    // boolean deleted：实际占用4字节（JVM对齐）
    long deletedSize = 4;
    
    long objectOverhead = objectHeader + fieldReferences + timestampSize + deletedSize;
    
    // 字符串对象的大小（包括字符串对象头和字符数组）
    long stringSize = estimateStringSize(key) + estimateStringSize(value);
    
    return objectOverhead + stringSize;  // 返回总内存占用
}

// 估算字符串的内存占用
private static long estimateStringSize(String str) {
    if (str == null) return 0;
    // String对象头（12字节）+ hash字段（4字节）+ char数组引用（4字节）
    long stringObjectSize = 12 + 4 + 4;
    // char数组：数组头（12字节）+ 字符数据（每字符2字节）
    long charArraySize = 12 + str.length() * 2;
    return stringObjectSize + charArraySize;
}
```

**代码解释**: 这个方法估算KeyValue对象的内存占用。理解内存使用模式有助于优化系统性能，特别是在内存受限的环境中。计算包括了JVM对象头、字段引用、以及字符串对象的实际内存占用。

## 常见问题

### 1. 为什么不直接物理删除？

**问题**: 为什么delete操作要插入墓碑而不是直接删除？

**答案**: 
- SSTable文件是不可变的，无法直接修改
- 立即删除需要重写文件，性能开销巨大
- 墓碑确保删除操作的持久性和一致性

### 2. 墓碑何时被清理？

**清理时机**:
1. **压缩过程**: 合并SSTable时清理过期墓碑
2. **TTL过期**: 墓碑超过生存时间后清理
3. **手动压缩**: 用户主动触发的清理操作

### 3. 时间戳冲突怎么办？

**冲突场景**: 两个写入操作发生在同一毫秒内

**解决方案**:
```java
// 在实际实现中可以考虑使用纳秒或序列号
private static final AtomicLong SEQUENCE = new AtomicLong(0);  // 原子序列号生成器

public KeyValue(String key, String value) {
    this.key = key;
    this.value = value;
    // 毫秒时间戳 * 1000 + 序列号，确保唯一性
    this.timestamp = System.currentTimeMillis() * 1000 + SEQUENCE.incrementAndGet() % 1000;
    this.deleted = false;
}
```

**代码解释**: 通过将毫秒时间戳扩展到微秒级别，并添加原子序列号，我们可以确保即使在高并发情况下时间戳也是唯一的。这种方法结合了时间的自然排序特性和序列号的唯一性。

## 小结

KeyValue是LSM Tree的基础数据结构，它通过以下设计满足了LSM Tree的需求：

1. **时间戳**: 提供版本控制和冲突解决
2. **删除标记**: 支持LSM Tree的删除语义
3. **不可变性**: 确保线程安全和数据一致性
4. **轻量级**: 最小化内存和CPU开销

## 下一步学习

理解了KeyValue的设计后，我们将学习它如何在MemTable中使用：

继续阅读：[第3章：MemTable 内存表](03-memtable-skiplist.md)

---

## 思考题

1. 如果系统时钟回拨，时间戳会出现什么问题？如何解决？
2. 墓碑标记会一直存在吗？什么情况下可以安全清理？
3. 如何优化KeyValue的内存使用？

**下一章预告**: 我们将深入学习MemTable的实现，理解跳表的工作原理和并发控制机制。 