# Java LSM Tree 实现教程

欢迎来到 Java LSM Tree 实现的完整教程！本教程基于一个生产级的 Java LSM Tree 项目，深入讲解 LSM Tree 的核心原理、架构设计以及代码实现细节。无论你是想学习数据库内核原理，还是想提升 Java 系统编程能力，本教程都能为你提供扎实的理论基础和实践经验。

> **项目亮点**：
>
> - **高性能写入**: O(log N) 写入性能
> - **数据持久化**: WAL (Write-Ahead Log) 确保崩溃恢复
> - **自动压缩**: 支持 Level-based 和 Size-Tiered 两种压缩策略
> - **并发安全**: 使用 ReadWriteLock 实现读写分离
> - **生产级特性**: 布隆过滤器、范围查询、LZ4 压缩、缓存系统 (LRU/LFU)、监控指标 (Micrometer) 等

## 教程目录

本教程共分为三个部分，循序渐进地带领你掌握 LSM Tree 的实现。

### 第一部分：核心概念与基础组件

1. **[LSM Tree 概述](01-lsm-tree-overview.md)**
   - 了解 LSM Tree 的设计初衷、读写流程及架构演进。
   - 对比 B+ 树与 LSM 树的优劣势。
2. **[KeyValue 数据结构](02-keyvalue-structure.md)**
   - 探索 LSM Tree 中最基础的数据单元设计。
   - 理解追加写入（Append-only）与不可变性（Immutability）。
3. **[MemTable 内存表](03-memtable-skiplist.md)**
   - 深入 `ConcurrentSkipListMap` 跳表实现。
   - 掌握内存中的数据排序与并发写入机制。

### 第二部分：磁盘存储与持久化

4. **[SSTable 磁盘存储](04-sstable-disk-storage.md)**
   - 解析 SSTable 的物理文件格式（Data Block, Index Block, Footer）。
   - 学习如何设计高效的磁盘查找算法。
5. **[布隆过滤器](05-bloom-filter.md)**
   - 理解布隆过滤器的数学原理与误判率控制。
   - 学习如何利用布隆过滤器优化读性能。
6. **[WAL 写前日志](06-wal-logging.md)**
   - 掌握数据库崩溃恢复（Crash Recovery）的核心机制。
   - 理解 ACID 特性在 LSM Tree 中的体现。

### 第三部分：高级特性与架构整合

7. **[压缩策略](07-compaction-strategy.md)**
   - 深入对比 Size-Tiered 与 Leveled Compaction 策略。
   - 理解读放大、写放大与空间放大的权衡（Trade-off）。
8. **[LSM Tree 主体协调](08-lsm-tree-main.md)**
   - 整合所有组件，构建完整的 KV 存储引擎。
   - 学习读写锁（ReadWriteLock）在读写分离中的应用。

---

## 给不同阶段学习者的建议

为了帮助你更高效地学习，我们针对不同背景的读者提供了定制化的学习路径和建议。

### 初级开发者 / 学生 (Beginner)

**目标**：理解 LSM Tree 的基本工作原理，跑通代码，完成基本的 Put/Get 操作。

- **推荐路径**：
  1. 阅读 **[01-lsm-tree-overview.md](01-lsm-tree-overview.md)**，重点理解 "追加写入" 和 "分层存储" 的概念。
  2. 跳过复杂的数学推导（如布隆过滤器的公式），直接阅读 **[03-memtable-skiplist.md](03-memtable-skiplist.md)** 和 **[08-lsm-tree-main.md](08-lsm-tree-main.md)**。
  3. 运行项目中的 `LSMTreeExample`，观察控制台输出，建立感性认识。
- **学习重点**：
  - 为什么 LSM Tree 写数据比 B+ 树快？
  - 数据在内存（MemTable）和磁盘（SSTable）中是如何流转的？
  - 什么是 WAL，为什么需要它？

### 中级开发者 (Intermediate Developer)

**目标**：掌握 Java 系统编程技巧，理解数据结构设计细节，能够修改和调试代码。

- **推荐路径**：
  - 按顺序阅读 **01-08** 所有章节。
  - 重点关注 **[04-sstable-disk-storage.md](04-sstable-disk-storage.md)** 中的文件格式设计，尝试手写一个简单的文件读写器。
  - 深入研究 **[05-bloom-filter.md](05-bloom-filter.md)** 和 **[07-compaction-strategy.md](07-compaction-strategy.md)** 的实现逻辑。
- **动手实践**：
  - 尝试修改 `MemTable` 的阈值，观察 Flush 的触发频率。
  - 在 `Compaction` 过程中添加日志，观察文件合并的过程。
  - 使用 IDE 的调试功能，跟踪一次 `get(key)` 请求的完整路径（MemTable -> Immutable MemTable -> SSTable）。

### 架构师 / 高级工程师 (Architect / Advanced)

**目标**：深入理解存储引擎的设计权衡（Trade-offs），关注性能优化、并发控制和系统稳定性。

- **推荐路径**：
  - 快速浏览基础章节，重点研读 **[06-wal-logging.md](06-wal-logging.md)**（关于数据一致性）和 **[07-compaction-strategy.md](07-compaction-strategy.md)**（关于读写放大）。
  - 关注 **[08-lsm-tree-main.md](08-lsm-tree-main.md)** 中的并发控制模型（ReadWriteLock 的使用粒度）。
- **思考题**：
  - 如果将跳表（SkipList）换成 B+ 树或红黑树作为 MemTable，会有什么影响？
  - 在极端的写压力下，Leveled Compaction 会导致怎样的写停顿（Write Stall）？如何优化？
  - 如何设计一个零拷贝（Zero-Copy）的 SSTable 读取路径？
  - 在多线程环境下，如何保证 WAL 和 MemTable 写入的原子性？
- **进阶实战 (Road to Production)**：
  - 如果你想将这个教学项目进化为生产级存储引擎，请务必参考 **[高级开发任务](advanced/advanced-tasks.md)**。
  - 该文档包含 12 个从 I/O 优化、缓存设计到故障恢复的实战任务，适合作为系统重构的蓝图。
  - 同时推荐阅读 **[生产就绪性分析](advanced/production-readiness-analysis.md)**，了解现有实现的局限性。

---

## 如何使用本教程

### 前置知识

在开始之前，建议你具备以下基础：

- **Java 语言**：熟悉 Java 8+ 语法，特别是 NIO (FileChannel, ByteBuffer) 和并发包 (JUC)。
- **数据结构**：了解数组、链表、树（二叉树、红黑树）、哈希表。
- **操作系统**：对文件系统、缓存（Page Cache）、磁盘 I/O 有基本概念。

### 环境准备

确保你的开发环境已就绪：

```bash
# 1. 克隆项目
git clone https://github.com/brianxiadong/java-lsm-tree.git
cd java-lsm-tree

# 2. 编译项目 (推荐使用构建脚本)
./build.sh build
# 或者使用 Maven
mvn clean compile

# 3. 运行测试 (推荐使用测试套件)
./test-suite/test-suite.sh unit
# 或者使用 Maven
mvn test

# 4. 运行示例
# 可以直接在 IDE 中运行 src/main/java/com/brianxiadong/lsmtree/LSMTreeExample.java
```

## 🤝 反馈与贡献

如果你发现教程中有错误，或者有更好的实现思路，欢迎提交 Issue 或 Pull Request。让我们一起完善这个项目，帮助更多的开发者掌握存储引擎的核心技术！

---

**开始探索吧！** 🚀
建议从第一章 [01-lsm-tree-overview.md](01-lsm-tree-overview.md) 开始阅读。
