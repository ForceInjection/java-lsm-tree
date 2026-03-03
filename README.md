# Java LSM Tree 实现

![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)
![Java](https://img.shields.io/badge/java-8+-orange.svg)
![Maven](https://img.shields.io/badge/maven-3.8+-green.svg)

一个用 Java 实现的 Log-Structured Merge Tree (LSM Tree) 数据结构，包含所有 LSM Tree 的核心特性。

## 1. 简介与特性

### 1.1 什么是 LSM Tree

LSM Tree（Log-Structured Merge Tree）是一种专为写密集型工作负载优化的数据结构，核心思想是**将随机写入转换为顺序写入**，从而充分利用磁盘的顺序访问性能优势。它被广泛应用于现代分布式数据库系统（如 LevelDB, RocksDB, Cassandra, HBase）。

### 1.2 核心特性

- ✅ **高性能写入**: O(log N) 写入性能，将随机 IO 转为顺序 IO
- ✅ **数据持久化**: WAL (Write-Ahead Log) 确保崩溃恢复
- ✅ **自动压缩**: 支持 Leveled (LevelDB风格) 和 Size-Tiered (Cassandra风格) 策略
- ✅ **并发安全**: 读写锁分离，写操作互斥，读操作并发
- ✅ **空间优化**: 布隆过滤器减少无效磁盘查询
- ✅ **高级查询**: 支持范围查询 (Range Query) 和快照读取

### 1.3 架构设计

```text
写入流程: Write -> WAL -> MemTable -> (满了) -> SSTable
查询流程: MemTable -> Immutable MemTables -> SSTables (按时间倒序)
分层压缩: Level 0 -> Level 1 -> Level 2 ... (自动合并与清理)
```

---

## 2. 循序渐进教程

本项目不仅是一个高性能的 KV 存储引擎，更是一套完整的 LSM Tree 学习资源。我们提供了**8 天学习计划**，循序渐进地带领你掌握数据库内核开发。

### 2.1 教程概览

- **[教程主页](tutorials/README.md)**：查看完整的学习路径、设计图解与代码实现细节。

### 2.2 完整课程表

- **第一天**：[LSM Tree 概述与架构设计](tutorials/01-lsm-tree-overview.md)
- **第二天**：[KeyValue 数据结构与编码](tutorials/02-keyvalue-structure.md)
- **第三天**：[MemTable 与跳表实现](tutorials/03-memtable-skiplist.md)
- **第四天**：[SSTable 磁盘文件格式](tutorials/04-sstable-disk-storage.md)
- **第五天**：[布隆过滤器实现](tutorials/05-bloom-filter.md)
- **第六天**：[WAL 写前日志与崩溃恢复](tutorials/06-wal-logging.md)
- **第七天**：[LSM Tree 压缩策略](tutorials/07-compaction-strategy.md)
- **第八天**：[LSM Tree 主体实现与整合](tutorials/08-lsm-tree-main.md)

> 💡 **进阶挑战**：如果你已经掌握了基础，请尝试完成 [Advanced Tasks](tutorials/advanced/advanced-tasks.md) 中的 12 个实战任务，将你的存储引擎打造为生产级产品。

---

## 3. 快速开始

### 3.1 环境要求

- Java 8 或更高版本
- Maven 3.6 或更高版本

### 3.2 安装与构建

```bash
# 克隆项目
git clone https://github.com/brianxiadong/java-lsm-tree.git
cd java-lsm-tree

# 推荐使用构建脚本
./build.sh build

# 运行测试
./build.sh test
```

### 3.3 基础使用示例

```java
import com.brianxiadong.lsmtree.LSMTree;

// 创建实例 (数据目录, MemTable最大条目数)
try (LSMTree db = new LSMTree("./data", 10000)) {
    // 写入数据
    db.put("user:1", "Alice");

    // 查询数据
    String value = db.get("user:1"); // 返回 "Alice"

    // 删除数据
    db.delete("user:1");
} // 自动关闭资源
```

---

## 4. 核心文档

本项目的技术文档深入剖析了实现细节与性能优化，建议按顺序阅读：

### 4.1 核心原理与源码分析

- [01-lsm-tree-intro.md](docs/01-lsm-tree-intro.md) - LSM Tree 简介与设计动机
- [02-lsm-tree-deep-dive.md](docs/02-lsm-tree-deep-dive.md) - 深入理解 LSM Tree 架构
- [03-skiplist-technical-guide.md](docs/03-skiplist-technical-guide.md) - 内存表(MemTable)与跳表实现细节
- [04-source-code-analysis.md](docs/04-source-code-analysis.md) - Java 实现源码深度解析

### 4.2 测试与性能优化

- [05-test-suite-guide.md](docs/05-test-suite-guide.md) - 自动化测试套件使用指南
- [06-benchmark-guide.md](docs/06-benchmark-guide.md) - 性能基准测试运行指南
- [07-performance-analysis-guide.md](docs/07-performance-analysis-guide.md) - 性能瓶颈分析与调优方法论

### 4.3 运维与工具

- [08-db-analyzer-guide.md](docs/08-db-analyzer-guide.md) - SSTable 数据文件分析工具使用
- [09-wal-analyzer-guide.md](docs/09-wal-analyzer-guide.md) - WAL 预写日志分析工具使用

---

## 5. 性能表现

在现代硬件环境 (Java 8, SSD) 下的基准测试结果：

### 5.1 核心指标

| 操作类型     | 吞吐量 (ops/sec) | 延迟 (P99) | 说明                          |
| :----------- | :--------------- | :--------- | :---------------------------- |
| **顺序写入** | ~700,000         | 1.9μs      | 极致的写入性能                |
| **随机写入** | ~450,000         | 2.5μs      | 依然保持极高吞吐              |
| **读取**     | ~3,500           | -          | 受限于磁盘 IO，需依赖缓存优化 |

### 5.2 性能特征总结

- **写入优化**: 写入性能极高，适合日志、监控等场景。
- **SSTable 分析**: 百万级条目的大文件分析仅需几十毫秒。
- **压缩开销**: 开启 LZ4 压缩会增加少量 CPU 开销，但显著减少磁盘占用。

> 详细测试报告请参考 [性能基准测试](docs/06-benchmark-guide.md)

---

## 6. 开发者指南

### 6.1 项目结构

```text
java-lsm-tree/
├── src/main/java/       # 核心源码 (LSMTree, MemTable, SSTable, WAL)
├── src/test/java/       # 单元测试与基准测试
├── docs/                # 技术文档
├── tutorials/           # 循序渐进的教程
├── test-suite/          # 自动化测试脚本集
├── analyze-db.sh        # 数据库分析工具
└── build.sh             # 构建脚本
```

### 6.2 核心组件

- **MemTable**: 基于 `ConcurrentSkipListMap` 实现的内存有序表。
- **SSTable**: 自定义文件格式，包含 DataBlock、BloomFilter 和稀疏索引。
- **WAL**: 使用 `FileChannel` 实现的预写日志，支持批量刷盘。
- **Compaction**: 独立的后台线程池执行合并任务，支持插件化策略。

### 6.3 扩展功能与路线图

- [✓] 基础 CRUD 与持久化
- [✓] Leveled & Size-Tiered 压缩
- [✓] 布隆过滤器
- [✓] 范围查询 (Range Query)
- [✓] 数据压缩 (LZ4)
- [✓] 指标监控 (Micrometer)
- [ ] **T1**: 范围查询反向扫描优化 (计划中)
- [ ] **T7**: 异步 I/O (Group Commit) (计划中)

> 完整开发计划请见 [Advanced Tasks](tutorials/advanced/advanced-tasks.md)

---

## 7. 贡献与许可

### 7.1 贡献代码

欢迎提交 Pull Request！建议先阅读 [测试套件指南](docs/05-test-suite-guide.md) 确保代码通过所有测试。

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/NewFeature`)
3. 提交更改并推送到分支
4. 创建 Pull Request

### 7.2 许可证

本项目采用 [Apache 2.0 许可证](LICENSE)。

### 7.3 作者

- **Brian Xia Dong** - [brianxiadong](https://github.com/brianxiadong)
- **Grissom Wang** - [Grissom Wang](https://github.com/grissomsh) (维护者)

---

⭐ **如果这个项目对你有帮助，请给个 Star！**
