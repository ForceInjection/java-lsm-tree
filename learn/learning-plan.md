# Java LSM Tree 14 天循序渐进学习计划

## 1. 学习计划概述

基于项目的源码解析文档和现有教程，制定了一个 14 天的循序渐进学习计划。每天包含理论学习、代码阅读、动手实践三个环节，确保深入理解 LSM Tree 的设计原理和实现细节。

## 2. 学习目标

- **理论掌握**: 深入理解 LSM Tree 的核心概念和设计原理
- **代码理解**: 熟悉 Java 实现的各个组件和算法细节
- **实战落地**: 在 14 天内完成 **T1 (Range Query)** 和 **T2 (Data Compression)** 两个高级任务的开发
- **工程能力**: 通过实际修改核心存储格式和查询引擎，提升系统级编程能力

## 3. 详细学习计划

### 3.1 第 1 天：LSM Tree 基础概念与项目环境搭建

#### 3.1.1 理论学习 (1-2 小时)

- 阅读 [docs/lsm-tree-intro.md](../docs/lsm-tree-intro.md) 第 1-3 章
- 阅读 [tutorials/01-lsm-tree-overview.md](../tutorials/01-lsm-tree-overview.md)
- 理解 LSM Tree 的基本概念、设计动机和应用场景

#### 3.1.2 代码阅读 (30 分钟)

- 浏览项目整体结构
- 查看 [README.md](../README.md) 了解项目特性

#### 3.1.3 动手实践 (1 小时)

```bash
# 1. 环境搭建
git clone https://github.com/brianxiadong/java-lsm-tree.git
cd java-lsm-tree
mvn clean compile

# 2. 运行基本示例
mvn exec:java -Dexec.mainClass="com.brianxiadong.lsmtree.LSMTreeExample"

# 3. 运行测试
mvn test
```

#### 3.1.4 性能分析任务 (1 小时)

```java
// 1. 搭建性能测试环境
// 2. 安装和配置性能监控工具 (JProfiler/VisualVM)
// 3. 运行基准测试，建立性能基线
// 4. 记录初始性能数据：内存使用、CPU 使用、基本读写性能
```

#### 3.1.5 学习检查点

- [ ] 能够解释 LSM Tree 的基本工作原理
- [ ] 成功搭建开发环境并运行示例
- [ ] 理解写优化 vs 读优化的权衡
- [ ] 建立了性能测试环境和基线数据

---

### 3.2 第 2 天：KeyValue 数据结构深入理解

#### 3.2.1 理论学习 (1 小时)

- 阅读 [docs/soucrce-code-analysis.md](../docs/soucrce-code-analysis.md) 第 3.1 节
- 阅读 [tutorials/02-keyvalue-structure.md](../tutorials/02-keyvalue-structure.md)
- 理解时间戳版本控制和墓碑标记机制

#### 3.2.2 代码阅读 (1 小时)

- 深入阅读 [KeyValue.java](../src/main/java/com/brianxiadong/lsmtree/KeyValue.java) 源码
- 理解 `Comparable` 接口的实现
- 分析排序规则的设计

#### 3.2.3 动手实践 (1-2 小时)

```java
// 创建测试文件：KeyValueTest.java
// 1. 测试KeyValue的排序行为
// 2. 验证时间戳版本控制
// 3. 测试墓碑标记的创建和识别
```

#### 3.2.4 学习检查点

- [ ] 理解 KeyValue 的排序规则
- [ ] 掌握多版本数据管理机制
- [ ] 能够创建和使用墓碑标记

---

### 3.3 第 3 天：MemTable 与跳表实现

#### 3.3.1 理论学习 (1-2 小时)

- 阅读 [docs/soucrce-code-analysis.md](../docs/soucrce-code-analysis.md) 第 3.2 节
- 阅读 [tutorials/03-memtable-skiplist.md](../tutorials/03-memtable-skiplist.md)
- 深入理解跳表数据结构的原理和优势

#### 3.3.2 代码阅读 (1 小时)

- 深入阅读 [MemTable.java](../src/main/java/com/brianxiadong/lsmtree/MemTable.java) 源码
- 理解 `ConcurrentSkipListMap` 的使用
- 分析内存管理和刷盘触发机制

#### 3.3.3 动手实践 (2 小时)

```java
// 1. 创建MemTable性能测试
public class MemTableBenchmark {
    // 测试插入性能
    // 测试查询性能
    // 测试并发访问
}

// 2. 实验不同大小的MemTable对性能的影响
// 3. 观察内存使用情况
```

#### 3.3.4 性能分析任务 (1 小时)

```java
// 1. MemTable 性能深度分析
public class MemTablePerformanceAnalysis {
    // 分析不同 MemTable 大小对写入性能的影响
    // 测试跳表查询性能与数据量的关系
    // 监控内存使用模式和 GC 影响
    // 分析刷盘触发时机对整体性能的影响
}
```

#### 3.3.5 学习检查点

- [ ] 理解跳表的时间复杂度优势
- [ ] 掌握 MemTable 的刷盘机制
- [ ] 能够进行 MemTable 性能测试
- [ ] 完成 MemTable 组件级性能分析

> **高级任务关联**：内存管理优化详见 [memory-optimization-guide.md](../docs/memory-optimization-guide.md) 中的 OptimizedMemTable 使用

---

### 3.4 第 4 天：SSTable 磁盘存储格式

#### 3.4.1 理论学习 (1-2 小时)

- 阅读 [docs/soucrce-code-analysis.md](../docs/soucrce-code-analysis.md) 第 3.3 节
- 阅读 [tutorials/04-sstable-disk-storage.md](../tutorials/04-sstable-disk-storage.md)
- 理解不可变文件的设计理念

#### 3.4.2 代码阅读 (1 小时)

- 深入阅读 [SSTable.java](../src/main/java/com/brianxiadong/lsmtree/SSTable.java) 源码
- 理解文件格式和序列化机制
- 分析查询优化策略

#### 3.4.3 动手实践 (2 小时)

```java
// 1. 创建SSTable文件格式分析工具
public class SSTableAnalyzer {
    // 读取并分析SSTable文件内容
    // 统计文件大小和条目数量
    // 验证数据有序性
}

// 2. 手动创建SSTable文件并验证读取
// 3. 测试大量数据的SSTable性能
```

#### 3.4.4 学习检查点

- [ ] 理解 SSTable 的文件格式
- [ ] 掌握不可变文件的优势
- [ ] 能够分析 SSTable 的存储效率

---

### 3.5 第 5 天：布隆过滤器原理与实现

#### 3.5.1 理论学习 (1-2 小时)

- 阅读 [docs/soucrce-code-analysis.md](../docs/soucrce-code-analysis.md) 第 3.5 节
- 阅读 [tutorials/05-bloom-filter.md](../tutorials/05-bloom-filter.md)
- 深入理解概率数据结构的原理

#### 3.5.2 代码阅读 (1 小时)

- 深入阅读 [BloomFilter.java](../src/main/java/com/brianxiadong/lsmtree/BloomFilter.java) 源码
- 理解哈希函数的实现
- 分析参数计算公式

#### 3.5.3 动手实践 (2 小时)

```java
// 1. 布隆过滤器参数优化实验
public class BloomFilterTuning {
    // 测试不同误报率的影响
    // 分析内存使用vs准确性的权衡
    // 验证哈希函数的分布均匀性
}

// 2. 创建布隆过滤器可视化工具
// 3. 对比有无布隆过滤器的查询性能
```

#### 3.5.4 学习检查点

- [ ] 理解布隆过滤器的数学原理
- [ ] 掌握参数调优方法
- [ ] 能够评估布隆过滤器的效果

---

### 3.6 第 6 天：WAL 写前日志机制

#### 3.6.1 理论学习 (1-2 小时)

- 阅读 [docs/soucrce-code-analysis.md](../docs/soucrce-code-analysis.md) 第 3.4 节
- 阅读 [tutorials/06-wal-logging.md](../tutorials/06-wal-logging.md)
- 理解数据持久性和崩溃恢复原理

#### 3.6.2 代码阅读 (1 小时)

- 深入阅读 [WriteAheadLog.java](../src/main/java/com/brianxiadong/lsmtree/WriteAheadLog.java) 源码
- 理解日志格式和恢复机制
- 分析文件 I/O 优化策略

#### 3.6.3 动手实践 (2 小时)

```java
// 1. 崩溃恢复测试
public class CrashRecoveryTest {
    // 模拟程序崩溃场景
    // 验证数据恢复的完整性
    // 测试恢复性能
}

// 2. WAL性能优化实验
// 3. 实现WAL文件轮转机制
```

#### 3.6.4 学习检查点

- [ ] 理解 WAL 的持久性保证
- [ ] 掌握崩溃恢复流程
- [ ] 能够优化 WAL 性能

> **高级任务关联**：WAL 性能优化详见 [advanced-io-optimization.md](../docs/advanced-io-optimization.md) 中的 P1: WAL 批量刷盘策略

---

### 3.7 第 7 天：压缩策略深入分析

#### 3.7.1 理论学习 (2 小时)

- 阅读 [docs/soucrce-code-analysis.md](../docs/soucrce-code-analysis.md) 第 3.6 节
- 阅读 [tutorials/07-compaction-strategy.md](../tutorials/07-compaction-strategy.md)
- 理解分层压缩的设计原理

#### 3.7.2 代码阅读 (1 小时)

- 深入阅读 [CompactionStrategy.java](../src/main/java/com/brianxiadong/lsmtree/CompactionStrategy.java) 源码
- 理解多路归并算法
- 分析压缩触发条件

#### 3.7.3 动手实践 (2-3 小时)

```java
// 1. 压缩策略可视化工具
public class CompactionVisualizer {
    // 展示压缩前后的文件分布
    // 统计压缩效果
    // 分析空间回收情况
}

// 2. 压缩性能测试
// 3. 实验不同压缩参数的影响
```

#### 3.7.4 性能分析任务 (1 小时)

```java
// 1. 压缩策略性能深度分析
public class CompactionPerformanceAnalysis {
    // 分析压缩对写入性能的影响
    // 测试不同压缩触发阈值的效果
    // 监控压缩过程中的 I/O 使用
    // 评估空间放大系数的变化
    // 分析压缩对读取性能的长期影响
}
```

#### 3.7.5 学习检查点

- [ ] 理解分层压缩策略
- [ ] 掌握多路归并算法
- [ ] 能够调优压缩参数
- [ ] 完成压缩策略性能分析

---

### 3.8 第 8 天：LSM Tree 主体架构与并发控制

#### 3.8.1 理论学习 (2 小时)

- 阅读 [docs/soucrce-code-analysis.md](../docs/soucrce-code-analysis.md) 第 3.7 节
- 阅读 [tutorials/08-lsm-tree-main.md](../tutorials/08-lsm-tree-main.md)
- 理解组件协调和并发控制机制

#### 3.8.2 代码阅读 (1-2 小时)

- 深入阅读 [LSMTree.java](../src/main/java/com/brianxiadong/lsmtree/LSMTree.java) 源码
- 理解读写锁的使用
- 分析写入和查询流程

#### 3.8.3 动手实践 (2 小时)

```java
// 1. 并发性能测试
public class ConcurrencyBenchmark {
    // 多线程读写测试
    // 锁竞争分析
    // 吞吐量测试
}

// 2. 流程追踪工具
// 3. 性能瓶颈分析
```

#### 3.8.4 学习检查点

- [ ] 理解 LSM Tree 的整体架构
- [ ] 掌握并发控制机制
- [ ] 能够进行并发性能测试

---

### 3.9 第 9 天：性能基准测试与分析

#### 3.9.1 理论学习 (1-2 小时)

- 阅读 [docs/performance-analysis-guide.md](../docs/performance-analysis-guide.md) 第 1-2 节
- 理解性能分析的基本方法论

#### 3.9.2 工具学习 (1 小时)

- 学习 JProfiler 的基本使用
- 学习 [test-suite/](../test-suite/) 性能测试工具

#### 3.9.3 动手实践 (3 小时)

```java
// 1. 扩展基准测试套件
public class ExtendedBenchmark {
    // 不同数据量的性能测试
    // 不同工作负载的测试
    // 延迟分布分析
}

// 2. 性能监控工具
// 3. 性能回归测试
```

#### 3.9.4 综合性能分析任务 (2 小时)

```java
// 1. 端到端性能分析
public class EndToEndPerformanceAnalysis {
    // 整体系统性能瓶颈识别
    // 不同工作负载下的性能特征分析
    // 读写混合场景的性能测试
    // 长期运行稳定性测试
    // 性能优化效果评估
    // 生成详细的性能分析报告
}
```

#### 3.9.5 学习检查点

- [ ] 能够设计性能测试场景
- [ ] 掌握性能数据分析方法
- [ ] 理解性能瓶颈识别技巧
- [ ] 完成端到端性能分析

---

### 3.10 第 10 天：完整应用场景实践

#### 3.10.1 理论学习 (1-2 小时)

- 阅读 [docs/performance-analysis-guide.md](../docs/performance-analysis-guide.md) 第 3-4 节
- 理解 JVM 内存管理和 GC 原理

#### 3.10.2 工具学习 (1 小时)

- 学习 JVisualVM 的使用
- 学习 test-suite 内存测试工具

#### 3.10.3 动手实践 (3-4 小时)

```java
// 1. 构建时序数据库应用
public class TimeSeriesDB {
    // 时间序列数据存储
    // 范围查询实现
    // 数据过期清理
}

// 2. 构建日志存储系统
// 3. 实现简单的KV存储服务
```

#### 3.10.4 学习检查点

- [ ] 能够构建完整的应用
- [ ] 掌握配置调优方法
- [ ] 理解生产环境考虑因素

---

### 3.11 第 11 天：实战 T1 - Range Query 基础实现

#### 3.11.1 任务目标 (T1 Phase 1)

- 实现 `RangeQuery` 接口的基础框架
- 实现多层数据（MemTable + SSTables）的 **Merging Iterator**
- 完成 **正向范围查询 (Forward Scan)** 功能

#### 3.11.2 关键技术点

- **迭代器设计**: 统一 `Iterator<KeyValue>` 接口，屏蔽内存和磁盘差异
- **归并排序**: 使用 `PriorityQueue` 实现多路归并
- **边界处理**: 正确处理 `startKey` (inclusive/exclusive) 和 `endKey`

#### 3.11.3 动手实践 (4 小时)

```java
// 1. 定义统一迭代器接口
public interface DBIterator extends Iterator<KeyValue> {
    void seek(String key);
    // ...
}

// 2. 实现 MergingIterator
public class MergingIterator implements DBIterator {
    // 管理多个子迭代器 (MemTableIterator, SSTableIterator)
    // 实时合并有序数据流
}

// 3. 单元测试
// 验证跨多层数据的正向查询正确性
```

#### 3.11.4 学习检查点

- [ ] 完成 `MergingIterator` 实现
- [ ] 通过正向查询的单元测试
- [ ] 理解多路归并对性能的影响

---

### 3.12 第 12 天：实战 T1 - Range Query 进阶与反向扫描

#### 3.12.1 任务目标 (T1 Phase 2)

- 实现高难度的 **反向范围查询 (Reverse Scan)**
- 处理 **Tombstone (墓碑)** 标记，确保删除的数据在范围查询中不可见
- 优化查询性能，减少不必要的 IO

#### 3.12.2 关键技术点

- **反向迭代**: 在单向链表（SkipList）和增量编码块中实现 `prev()` 操作的挑战
- **去重逻辑**: 在合并过程中，高层数据覆盖底层数据（含 Tombstone 处理）
- **性能优化**: 使用 BloomFilter 跳过不包含 range 的 SSTable（如果支持）

#### 3.12.3 动手实践 (4 小时)

```java
// 1. 实现反向迭代器
// MemTable: 使用 ConcurrentNavigableMap.descendingMap()
// SSTable: 实现块内的反向读取（可能需要加载整个 Block）

// 2. 完善结果去重
// 确保同一个 Key 的多个版本中，只返回最新的一个（如果是 Tombstone 则都不返回）

// 3. 综合测试
// 测试包含大量删除操作后的范围查询准确性
```

#### 3.12.4 学习检查点

- [ ] 完成 T1 任务的所有验收标准
- [ ] 反向查询性能符合预期
- [ ] 删除数据的可见性处理正确

---

### 3.13 第 13 天：实战 T2 - Data Compression 实现

#### 3.13.1 任务目标 (T2)

- 修改 SSTable 文件格式，支持 Block 级压缩
- 集成 **Snappy** 或 **LZ4** 压缩库
- 实现压缩策略的配置化

#### 3.13.2 关键技术点

- **SSTable 格式变更**: 在 Block 写入磁盘前进行压缩，读取时解压
- **库的选择**: 引入 `org.xerial.snappy:snappy-java` 或 `org.lz4:lz4-java`
- **Buffer 管理**: 压缩/解压过程中避免频繁的内存分配

#### 3.13.3 动手实践 (4 小时)

```java
// 1. 引入依赖并封装 Compression 接口
public interface Compression {
    byte[] compress(byte[] data);
    byte[] decompress(byte[] data);
}

// 2. 改造 SSTableWriter
// 在 flush Block 到磁盘前调用 compress

// 3. 改造 SSTableReader
// 在读取 Block 后判断压缩标志并解压
```

#### 3.13.4 学习检查点

- [ ] 成功集成压缩库
- [ ] 验证压缩后的 SSTable 文件体积显著减小
- [ ] 确保读写流程在开启压缩后依然正常

---

### 3.14 第 14 天：T1 & T2 集成测试与性能基准

#### 3.14.1 任务目标

- 对完成的 **Range Query (T1)** 和 **Data Compression (T2)** 进行综合验收
- 运行基准测试，对比开启/关闭压缩对吞吐量和延迟的影响
- 总结 14 天的学习成果

#### 3.14.2 动手实践 (4 小时)

```java
// 1. 功能回归测试
// 确保引入压缩后，Range Query 依然正确
// 确保反向扫描在压缩块上工作正常

// 2. 性能基准对比 (Benchmark)
// 场景 A: 纯写入 (开启压缩 vs 关闭压缩) -> 观察 IOPS 和磁盘占用
// 场景 B: 范围查询 (开启压缩 vs 关闭压缩) -> 观察 CPU 使用率和延迟

// 3. 编写 T1/T2 验收报告
```

#### 3.14.3 成果总结

- **T1 成果**: 具备了类似数据库的 `SELECT * FROM table WHERE key BETWEEN a AND b` 能力
- **T2 成果**: 磁盘空间节省 50%+, IO 吞吐量间接提升
- **核心能力**: 完成了从"玩具 demo"到"具备基础生产特性引擎"的跨越

#### 3.14.4 学习检查点

- [ ] 所有单元测试通过
- [ ] 产出 T1/T2 性能对比报告
- [ ] 代码符合项目规范，准备好合并
- [ ] 准备好进入下一阶段 (T3+)

---

## 4. 毕业与进阶

恭喜你完成了 14 天的 LSM Tree 核心实战训练！你现在已经拥有了一个具备 **Range Query** 和 **Data Compression** 能力的存储引擎原型。

接下来的进阶之路，请参考项目中的其他专项文档：

- **后续开发任务**: 详见 [advanced-tasks.md](./advanced-tasks.md) (包含 T3-T12 的完整开发路线图)
- **性能分析指南**: 详见 [docs/performance-analysis-guide.md](../docs/performance-analysis-guide.md) (包含系统性性能优化方法论、工具使用和指标体系)
- **更多学习资源**: 请查阅项目根目录的 [README.md](../README.md)

愿你在系统编程的道路上越走越远！

---
