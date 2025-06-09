# Java LSM Tree 实现

![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)
![Java](https://img.shields.io/badge/java-8+-orange.svg)
![Maven](https://img.shields.io/badge/maven-3.6+-green.svg)

一个用Java实现的Log-Structured Merge Tree (LSM Tree)数据结构，包含所有LSM Tree的核心特性。

## 特性

### 核心LSM Tree组件
- **MemTable**: 内存中的有序数据结构，使用跳表实现
- **SSTable**: 磁盘上的有序不可变文件
- **WAL (Write-Ahead Log)**: 写前日志，确保数据持久性
- **布隆过滤器**: 快速判断键是否可能存在
- **压缩策略**: 多级合并压缩，优化存储和查询性能

### 主要功能
- ✅ **高性能写入**: O(log N) 写入性能
- ✅ **高效查询**: 结合内存和磁盘的多层查询
- ✅ **数据持久化**: WAL确保崩溃恢复
- ✅ **自动压缩**: 后台自动执行SSTable合并
- ✅ **并发安全**: 读写锁保证线程安全
- ✅ **空间优化**: 布隆过滤器减少无效磁盘IO

## 架构设计

### LSM Tree结构
```
写入流程: Write -> WAL -> MemTable -> (满了) -> SSTable
查询流程: MemTable -> Immutable MemTables -> SSTables (按时间倒序)
```

### 分层压缩
```
Level 0: [SSTable] [SSTable] [SSTable] [SSTable]  (4个文件时触发压缩)
Level 1: [SSTable] [SSTable] ... (40个文件时触发压缩)  
Level 2: [SSTable] [SSTable] ... (400个文件时触发压缩)
...
```

## 快速开始

### 环境要求
- Java 8 或更高版本
- Maven 3.6 或更高版本

### 安装和构建
```bash
git clone https://github.com/brianxiadong/java-lsm-tree.git
cd java-lsm-tree
mvn clean compile
```

### 基本使用

```java
import com.brianxiadong.lsmtree.LSMTree;

// 创建LSM Tree实例
try (LSMTree lsmTree = new LSMTree("data", 1000)) {
    // 插入数据
    lsmTree.put("user:1", "Alice");
    lsmTree.put("user:2", "Bob");
    
    // 查询数据
    String value = lsmTree.get("user:1"); // 返回 "Alice"
    
    // 更新数据
    lsmTree.put("user:1", "Alice Updated");
    
    // 删除数据
    lsmTree.delete("user:2");
    
    // 强制刷盘
    lsmTree.flush();
    
    // 获取统计信息
    LSMTree.LSMTreeStats stats = lsmTree.getStats();
    System.out.println(stats);
}
```

### 运行示例
```bash
mvn exec:java -Dexec.mainClass="com.brianxiadong.lsmtree.LSMTreeExample"
```

### 运行测试
```bash
mvn test
```

## 核心组件详解

### 1. KeyValue
```java
// 基础数据结构，包含键、值、时间戳和删除标记
KeyValue kv = new KeyValue("key", "value");
KeyValue tombstone = KeyValue.createTombstone("key"); // 删除标记
```

### 2. MemTable
```java
// 内存中的有序表，基于跳表实现
MemTable memTable = new MemTable(1000);
memTable.put("key", "value");
String value = memTable.get("key");
```

### 3. SSTable
```java
// 磁盘上的有序文件
List<KeyValue> sortedData = Arrays.asList(/*...*/);
SSTable ssTable = new SSTable("data/table.db", sortedData);
String value = ssTable.get("key");
```

### 4. BloomFilter
```java
// 布隆过滤器，快速过滤不存在的键
BloomFilter filter = new BloomFilter(10000, 0.01);
filter.add("key");
boolean mightExist = filter.mightContain("key");
```

### 5. WAL (Write-Ahead Log)
```java
// 写前日志，确保数据持久性
WriteAheadLog wal = new WriteAheadLog("wal.log");
wal.append(WriteAheadLog.LogEntry.put("key", "value"));
List<WriteAheadLog.LogEntry> entries = wal.recover();
```

## 性能特征

### 时间复杂度
- **写入**: O(log N) - MemTable跳表插入
- **查询**: O(log N + K) - N为MemTable大小，K为SSTable数量
- **删除**: O(log N) - 插入删除标记

### 空间复杂度
- **内存**: MemTable + 索引 + 布隆过滤器
- **磁盘**: SSTable文件 + WAL日志

### 压缩策略
- **分层压缩**: Level-based compaction
- **触发条件**: 每层文件数量超过阈值
- **合并算法**: 多路归并排序 + 去重

## 配置参数

### LSMTree构造参数
```java
LSMTree(String dataDir, int memTableMaxSize)
```
- `dataDir`: 数据存储目录
- `memTableMaxSize`: MemTable最大条目数

### 压缩策略配置
```java
CompactionStrategy(String dataDir, int maxLevelSize, int levelSizeMultiplier)
```
- `maxLevelSize`: Level 0最大文件数 (默认: 4)
- `levelSizeMultiplier`: 级别大小倍数 (默认: 10)

## 项目结构

```
src/
├── main/java/com/brianxiadong/lsmtree/
│   ├── LSMTree.java              # 主要LSM Tree实现
│   ├── KeyValue.java             # 键值对数据结构
│   ├── MemTable.java             # 内存表
│   ├── SSTable.java              # 磁盘表
│   ├── BloomFilter.java          # 布隆过滤器
│   ├── WriteAheadLog.java        # 写前日志
│   ├── CompactionStrategy.java   # 压缩策略
│   └── LSMTreeExample.java       # 使用示例
└── test/java/com/brianxiadong/lsmtree/
    └── LSMTreeTest.java          # 单元测试
```

## 技术细节

### WAL格式
```
PUT|key|value|timestamp
DELETE|key||timestamp
```

### SSTable文件格式
```
[Entry Count: 4 bytes]
[Data Entries: Variable]
[Bloom Filter: Variable]
[Sparse Index: Variable]
```

### 布隆过滤器
- 使用Double Hashing避免多个哈希函数
- 可配置误报率 (默认: 1%)
- 支持序列化/反序列化

### 并发控制
- 使用ReadWriteLock实现读写分离
- 写操作互斥，读操作并发
- WAL写入同步，确保持久性

## 扩展功能

### 已实现
- [x] 基础CRUD操作
- [x] WAL日志恢复
- [x] 自动压缩
- [x] 布隆过滤器优化
- [x] 统计信息
- [x] 并发安全

### 计划中
- [ ] Range查询支持
- [ ] 数据压缩 (Snappy/LZ4)
- [ ] 更复杂的压缩策略
- [ ] 监控和度量
- [ ] 分区支持

## 贡献

欢迎贡献代码！请遵循以下步骤：

1. Fork项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建Pull Request

## 许可证

本项目采用Apache 2.0许可证 - 查看 [LICENSE](LICENSE) 文件了解详情

## 参考资料

- [The Log-Structured Merge-Tree (LSM-Tree)](http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.44.2782&rep=rep1&type=pdf)
- [LevelDB Documentation](https://github.com/google/leveldb/blob/main/doc/index.md)
- [RocksDB Wiki](https://github.com/facebook/rocksdb/wiki)

## 作者

**Brian Xia Dong** - [brianxiadong](https://github.com/brianxiadong)

---

⭐ 如果这个项目对你有帮助，请给个Star！ 