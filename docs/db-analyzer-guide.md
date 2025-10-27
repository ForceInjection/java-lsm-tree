# SSTable 文件分析工具使用指南

## 1. 概述

SSTable 文件分析工具是 LSM Tree 存储引擎的专业磁盘文件分析和调试工具，基于 `com.brianxiadong.lsmtree.tools.SSTableAnalyzer` 核心类实现。该工具专注于 SSTable（Sorted String Table）文件的深度解析，提供完整的文件结构分析、数据完整性验证和存储效率评估功能，支持压缩策略优化和性能调优场景。

### 1.1 核心特性

- **精确文件解析**: 基于 `SSTable.readFromFile()` 解析器完整解析 SSTable 二进制格式
- **数据完整性验证**: 检验键排序、索引一致性和文件结构完整性
- **存储效率分析**: 统计压缩比、空间利用率和删除标记分布
- **多维度统计**: 提供键值分布、时间戳分析和访问模式识别
- **批量处理能力**: 支持目录级别的批量分析和对比功能

---

## 2. 功能特性

### 2.1 文件结构分析

#### 2.1.1 SSTable 格式解析

- **文件头解析**: 解析 SSTable 文件头的元数据信息，包括版本号、索引偏移量和数据块数量
- **索引结构分析**: 分析稀疏索引的组织结构，验证索引项的完整性和正确性
- **数据块检查**: 检查数据块的边界、压缩状态和内部结构完整性
- **文件完整性验证**: 验证文件格式规范性和数据一致性

#### 2.1.2 存储布局分析

- **空间分布统计**: 分析索引区、数据区的空间占用比例
- **压缩效果评估**: 计算数据压缩比和存储空间利用率
- **碎片化检测**: 识别存储碎片和空间浪费情况

### 2.2 数据内容分析

#### 2.2.1 键值对分析

- **键分布统计**: 分析键的长度分布、字符集使用和排序正确性
- **值类型识别**: 识别值的数据类型和大小分布模式
- **删除标记检测**: 统计删除标记（tombstone）的数量和分布
- **时间戳分析**: 分析记录的时间戳分布和时序特征

#### 2.2.2 数据质量检查

- **排序验证**: 验证键的字典序排序正确性
- **重复键检测**: 检测可能存在的重复键或数据异常
- **数据完整性**: 验证键值对的完整性和格式正确性

### 2.3 性能分析

#### 2.3.1 访问模式分析

- **热点数据识别**: 分析数据访问的热点分布
- **查询效率评估**: 评估索引查询的效率和性能特征
- **缓存友好性**: 分析数据布局对缓存性能的影响

#### 2.3.2 存储优化建议

- **压缩策略建议**: 基于数据特征推荐最优压缩策略
- **合并时机建议**: 分析文件合并的最佳时机和策略
- **索引优化**: 提供索引结构优化建议

### 2.4 数据导出与比较

#### 2.4.1 多格式导出

- **结构化导出**: 支持 JSON、CSV、XML 等结构化格式导出
- **原始数据导出**: 提供二进制和文本格式的原始数据导出
- **统计报告生成**: 生成详细的分析报告和统计摘要

#### 2.4.2 批量处理能力

- **目录级分析**: 支持对整个目录的 SSTable 文件进行批量分析
- **文件对比**: 提供多个 SSTable 文件之间的差异对比功能
- **自动化分析**: 支持脚本化和自动化的分析流程

---

## 3. SSTable 文件格式规范

### 3.1 文件结构概述

SSTable（Sorted String Table）文件采用二进制格式存储，由文件头、稀疏索引区和数据块区三个主要部分组成：

```text
+------------------+
|    文件头区域     |  (固定大小)
+------------------+
|   稀疏索引区域    |  (可变大小)
+------------------+
|    数据块区域     |  (可变大小)
+------------------+
```

### 3.2 文件头格式

文件头包含 SSTable 的元数据信息，采用固定长度格式：

| 字段名称      | 偏移量 | 大小    | 类型 | 描述               |
| ------------- | ------ | ------- | ---- | ------------------ |
| Magic Number  | 0      | 4 bytes | int  | 文件格式标识符     |
| Version       | 4      | 4 bytes | int  | 文件格式版本号     |
| Index Offset  | 8      | 8 bytes | long | 稀疏索引起始偏移量 |
| Index Count   | 16     | 4 bytes | int  | 索引项数量         |
| Data Offset   | 20     | 8 bytes | long | 数据块起始偏移量   |
| Total Records | 28     | 4 bytes | int  | 总记录数量         |
| File Size     | 32     | 8 bytes | long | 文件总大小         |

### 3.3 稀疏索引格式

稀疏索引区域存储键到数据块的映射关系，每个索引项包含：

| 字段名称     | 大小    | 类型   | 描述               |
| ------------ | ------- | ------ | ------------------ |
| Key Length   | 4 bytes | int    | 键的字节长度       |
| Key Data     | 可变    | byte[] | 键的实际数据       |
| Block Offset | 8 bytes | long   | 对应数据块的偏移量 |
| Block Size   | 4 bytes | int    | 数据块的大小       |
| Record Count | 4 bytes | int    | 该块中的记录数量   |

### 3.4 数据块格式

数据块存储实际的键值对数据，每个记录的格式为：

| 字段名称     | 大小    | 类型    | 描述         |
| ------------ | ------- | ------- | ------------ |
| Key Length   | 4 bytes | int     | 键的字节长度 |
| Value Length | 4 bytes | int     | 值的字节长度 |
| Timestamp    | 8 bytes | long    | 记录时间戳   |
| Deleted Flag | 1 byte  | boolean | 删除标记     |
| Key Data     | 可变    | byte[]  | 键的实际数据 |
| Value Data   | 可变    | byte[]  | 值的实际数据 |

### 3.5 数据组织规则

#### 3.5.1 排序规则

- 所有键按字典序严格排序
- 同一键的多个版本按时间戳降序排列
- 删除标记（tombstone）保持原有排序位置

#### 3.5.2 压缩策略

- 支持块级别的数据压缩
- 压缩算法可配置（LZ4、Snappy、GZIP）
- 索引区域不进行压缩以保证快速访问

#### 3.5.3 完整性保证

- 文件头包含校验和信息
- 每个数据块包含独立的校验和
- 支持增量校验和快速验证

## 4. 安装和编译

确保项目已正确编译：

```bash
./build.sh
```

## 4. 使用方法

### 4.1 基本语法

```bash
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer [选项] <SSTable文件路径>
```

### 4.2 命令行选项

#### 4.2.1 基础选项

| 选项 | 长选项      | 参数 | 描述                               |
| ---- | ----------- | ---- | ---------------------------------- |
| `-h` | `--help`    | 无   | 显示完整的帮助信息和使用示例       |
| `-v` | `--verbose` | 无   | 启用详细输出模式，显示解析过程信息 |
| `-q` | `--quiet`   | 无   | 静默模式，仅输出关键信息和错误     |
| `-V` | `--version` | 无   | 显示工具版本信息                   |

#### 4.2.2 分析选项

| 选项 | 长选项       | 参数 | 描述                                       |
| ---- | ------------ | ---- | ------------------------------------------ |
| `-s` | `--stats`    | 无   | 显示文件统计信息（大小、记录数、压缩比等） |
| `-d` | `--data`     | 无   | 显示键值对数据内容                         |
| `-i` | `--index`    | 无   | 显示稀疏索引结构信息                       |
| `-m` | `--metadata` | 无   | 显示文件头元数据信息                       |
| `-c` | `--check`    | 无   | 执行完整性检查和数据验证                   |

#### 4.2.3 输出控制选项

| 选项 | 长选项        | 参数            | 描述                               |
| ---- | ------------- | --------------- | ---------------------------------- |
| `-l` | `--limit`     | `<数量>`        | 限制显示的记录数量                 |
| `-f` | `--filter`    | `<模式>`        | 按键模式过滤记录（支持正则表达式） |
| `-r` | `--range`     | `<开始>:<结束>` | 指定键范围进行分析                 |
| `-t` | `--timestamp` | `<时间戳>`      | 按时间戳过滤记录                   |

#### 4.2.4 导出选项

| 选项         | 长选项     | 参数     | 描述                              |
| ------------ | ---------- | -------- | --------------------------------- |
| `-e`         | `--export` | `<格式>` | 导出数据格式：json、csv、xml、txt |
| `-o`         | `--output` | `<文件>` | 指定输出文件路径                  |
| `--pretty`   | 无         | 无       | 格式化输出（适用于 JSON 和 XML）  |
| `--compress` | 无         | 无       | 压缩输出文件                      |

#### 4.2.5 比较和批量选项

| 选项          | 长选项 | 参数       | 描述                                  |
| ------------- | ------ | ---------- | ------------------------------------- |
| `--compare`   | 无     | `<文件2>`  | 比较两个 SSTable 文件的差异           |
| `--batch`     | 无     | `<目录>`   | 批量分析目录中的所有 SSTable 文件     |
| `--recursive` | 无     | 无         | 递归处理子目录（与 --batch 配合使用） |
| `--parallel`  | 无     | `<线程数>` | 并行处理文件数量                      |

### 4.3 使用示例

#### 4.3.1 基础分析操作

```bash
# 基本文件分析
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer data/sstable_001.sst

# 显示详细统计信息
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer -s -v data/sstable_001.sst

# 检查文件完整性
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer -c data/sstable_001.sst

# 显示文件元数据
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer -m data/sstable_001.sst
```

#### 4.3.2 数据查看和过滤

```bash
# 显示前100条记录
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer -d -l 100 data/sstable_001.sst

# 按键模式过滤
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer -d -f "user_.*" data/sstable_001.sst

# 按键范围查询
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer -d -r "key001:key999" data/sstable_001.sst

# 按时间戳过滤
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer -d -t 1640995200000 data/sstable_001.sst
```

#### 4.3.3 数据导出功能

```bash
# 导出为格式化 JSON
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer -e json --pretty -o output.json data/sstable_001.sst

# 导出为 CSV 格式
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer -e csv -o output.csv data/sstable_001.sst

# 导出压缩文件
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer -e json --compress -o output.json.gz data/sstable_001.sst

# 导出统计报告
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer -s -e xml -o report.xml data/sstable_001.sst
```

#### 4.3.4 文件比较分析

```bash
# 基本文件比较
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer --compare data/sstable_002.sst data/sstable_001.sst

# 详细比较并导出差异
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer --compare data/sstable_002.sst -v -o diff.txt data/sstable_001.sst

# 比较特定键范围
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer --compare data/sstable_002.sst -r "key001:key100" data/sstable_001.sst
```

#### 4.3.5 批量处理操作

```bash
# 批量分析目录
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer --batch data/

# 递归分析所有子目录
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer --batch --recursive data/

# 并行批量分析
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer --batch --parallel 4 data/

# 批量生成统计报告
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer --batch -s -e json -o batch_report.json data/
```

### 4.4 输出格式说明

#### 4.4.1 控制台输出格式

工具的控制台输出采用结构化格式，主要包含以下部分：

```bash
=== SSTable 文件分析报告 ===
文件路径: /path/to/sstable_001.sst
文件大小: 1.2 MB
记录数量: 10,000
索引项数: 100
压缩比: 65.4%

=== 文件头信息 ===
Magic Number: 0x53535442
版本号: 1
索引偏移: 1024
数据偏移: 5120
总记录数: 10000

=== 统计信息 ===
平均键长度: 12.5 bytes
平均值长度: 128.3 bytes
删除标记数: 150
最新时间戳: 2024-01-15 10:30:45
最旧时间戳: 2024-01-10 08:15:20
```

#### 4.4.2 性能指标解读

- **压缩比**: 数据压缩后与原始大小的比例，越高表示压缩效果越好
- **索引密度**: 索引项数量与总记录数的比例，影响查询性能
- **删除标记比例**: 删除记录占总记录的比例，过高时建议执行压缩
- **键分布均匀度**: 键的分布是否均匀，影响负载均衡效果

#### 4.4.3 数据内容输出格式

当使用 `-d` 选项显示数据内容时，输出格式如下：

```bash
=== 数据内容 ===
键                    状态       值                              时间戳
--------------------------------------------------------------------------------
key001               活跃       value001                        2024-01-15 14:30:25
key002               已删除     -                               2024-01-15 14:30:26
key003               活跃       value003                        2024-01-15 14:30:27
...
```

#### 4.4.4 文件对比输出格式

当使用 `--compare` 选项比较两个文件时，输出格式如下：

```bash
=== SSTable 文件对比 ===
属性                 文件1                          文件2
--------------------------------------------------------------------------------
文件路径             data/level0/table1.db          data/level1/table2.db
文件大小             2.5 KB                         3.1 KB
条目数量             100                            120
活跃条目             85                             110
删除条目             15                             10
存储效率             0.85                           0.92
```

### 4.5 最佳实践

#### 4.5.1 高效使用技巧

```bash
# 快速检查文件状态
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer -s -q data/file.sst

# 分析大文件时限制输出
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer -d -l 100 data/large_file.sst

# 批量分析时使用静默模式
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer --batch -q data/
```

#### 4.5.2 数据安全注意事项

- **只读访问**: 工具仅进行只读分析，不会修改原始数据文件
- **生产环境使用**: 建议先在测试环境验证工具行为
- **文件权限**: 确保工具运行用户具有适当的文件读取权限

#### 4.5.3 输出结果解读

```bash
# 查看关键统计指标
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer -s data/file.sst | grep -E "(Compression|Entries|Size)"

# 导出详细报告用于分析
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer -o analysis_report.json data/file.sst
```

---

## 5. 应用场景

### 5.1 开发调试场景

#### 5.1.1 数据完整性验证

在开发过程中验证 SSTable 文件的数据完整性：

```bash
# 检查文件完整性
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer -c data/test.sst

# 验证数据内容
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer -d data/test.sst
```

#### 5.1.2 性能问题诊断

分析 SSTable 文件的性能特征，识别潜在的性能瓶颈：

```bash
# 分析文件性能指标
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer -s data/performance_test.sst

# 比较不同版本的性能差异
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer --compare data/v1.sst data/v2.sst
```

### 5.2 运维监控场景

#### 5.2.1 存储空间监控

监控 SSTable 文件的存储使用情况：

```bash
# 批量分析存储使用情况
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer --batch -s data/

# 导出存储报告
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer --batch -o storage_report.json data/
```

#### 5.2.2 数据质量监控

定期检查数据质量和文件健康状况：

```bash
# 定期健康检查脚本
#!/bin/bash
for file in data/*.sst; do
    echo "Checking $file..."
    java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer -c "$file"
done
```

### 5.3 数据迁移场景

#### 5.3.1 迁移前验证

在数据迁移前验证源文件的完整性：

```bash
# 验证迁移源文件
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer -c -v source_data/

# 导出迁移清单
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer --batch -o migration_inventory.json source_data/
```

#### 5.3.2 迁移后校验

迁移完成后验证数据的一致性：

```bash
# 比较迁移前后的数据
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer --compare source_data/file.sst target_data/file.sst

# 批量验证迁移结果
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer --batch --compare source_data/ target_data/
```

---

## 6. 使用注意事项

### 6.1 常见问题与解决方案

#### 6.1.1 文件访问问题

- **文件不存在错误**: 检查文件路径是否正确，确认文件具有读取权限
- **权限不足**: 确保当前用户对 SSTable 文件和目录具有读取权限
- **文件锁定**: 确认文件未被其他进程占用或锁定

#### 6.1.2 文件格式问题

- **格式不兼容**: 确认文件是有效的 SSTable 文件，检查文件头的 Magic Number
- **文件损坏**: 使用 `-c` 选项进行完整性检查，识别损坏的数据块
- **版本不匹配**: 检查 SSTable 文件版本与工具支持的版本是否兼容

#### 6.1.3 性能和内存问题

- **内存不足**: 对于大文件使用 `-l` 选项限制显示条目数，或增加 JVM 堆内存
- **处理速度慢**: 使用 `--parallel` 选项启用并行处理，提高批量分析效率
- **磁盘空间不足**: 导出大量数据时确保有足够的磁盘空间

### 6.2 性能优化建议

#### 6.2.1 内存配置

```bash
# 为大文件分析增加内存
java -Xmx4g -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer data/large_file.sst

# 调整垃圾回收参数
java -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer
```

#### 6.2.2 批量处理优化

```bash
# 使用并行处理提高效率
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer --batch --parallel 8 data/

# 限制输出减少内存使用
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer --batch -s -q data/
```

### 6.3 调试和诊断

#### 6.3.1 详细日志

```bash
# 启用详细日志输出
java -Dlog.level=DEBUG -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer -v data/file.sst

# 输出到日志文件
java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzer -v data/file.sst > analysis.log 2>&1
```

#### 6.3.2 问题诊断流程

1. **基础检查**: 使用 `-c` 选项检查文件完整性
2. **元数据分析**: 使用 `-m` 选项查看文件头信息
3. **统计分析**: 使用 `-s` 选项获取详细统计信息
4. **数据采样**: 使用 `-d -l 10` 查看少量数据样本
5. **深度分析**: 根据初步结果进行针对性的深度分析

---

## 7. 相关文档

- [LSM Tree 架构文档](lsm-tree-deep-dive.md)
- [SSTable 实现详解](04-sstable-disk-storage.md)
- [性能分析指南](performance-analysis-guide.md)
