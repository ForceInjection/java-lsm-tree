# 第5章：布隆过滤器

## 什么是布隆过滤器？

**布隆过滤器 (Bloom Filter)** 是一种概率型数据结构，用于快速判断一个元素是否可能存在于集合中。它具有以下特性：

- **无假阴性**: 如果布隆过滤器说元素不存在，那么元素一定不存在
- **有假阳性**: 如果布隆过滤器说元素存在，元素可能不存在
- **空间高效**: 相比哈希表，内存占用极小
- **时间高效**: 查询和插入都是O(k)，k是哈希函数数量

## 布隆过滤器在LSM Tree中的作用

在LSM Tree中，布隆过滤器被广泛应用于SSTable文件中：

```
查询流程:
1. 检查MemTable (可能存在)
2. 检查Immutable MemTable (可能存在)  
3. 对每个SSTable:
   a. 检查布隆过滤器 (可能存在？)
   b. 如果可能存在，读取文件查找
   c. 如果不存在，直接跳过 (避免磁盘I/O)
```

**性能提升**: 布隆过滤器可以减少90%以上的无效磁盘I/O操作！

## 数学原理

### 基本参数

- **m**: 位数组大小
- **n**: 预期插入元素数量  
- **k**: 哈希函数数量
- **p**: 期望假阳性率

### 最优参数计算

```java
public class BloomFilterMath {
    
    // 计算最优位数组大小
    public static int optimalBitArraySize(int expectedEntries, double fpp) {
        return (int) Math.ceil(-expectedEntries * Math.log(fpp) / (Math.log(2) * Math.log(2)));
    }
    
    // 计算最优哈希函数数量
    public static int optimalHashFunctionCount(int bitArraySize, int expectedEntries) {
        return Math.max(1, (int) Math.round((double) bitArraySize / expectedEntries * Math.log(2)));
    }
    
    // 计算实际假阳性率
    public static double actualFalsePositiveRate(int bitArraySize, int hashFunctionCount, int insertedEntries) {
        return Math.pow(1 - Math.exp(-hashFunctionCount * (double) insertedEntries / bitArraySize), hashFunctionCount);
    }
}
```

### 参数关系图解

```
假阳性率 vs 位数组大小 (n=1000):
   
0.10 |     *
     |      *
0.05 |       *
     |        *
0.01 |         *
     |          *
0.001|           *
     +--+--+--+--+--+--+--+
     0  2k 4k 6k 8k 10k 12k  (位数组大小)

哈希函数数量 vs 假阳性率:
     
  8  |           *
     |          * 
  6  |         *
     |        *
  4  |       *
     |      *
  2  |     *
     +--+--+--+--+--+--+
    0.001 0.01 0.05 0.1  (假阳性率)
```

## 布隆过滤器实现

### 核心实现

```java
package com.brianxiadong.lsmtree;

import java.util.BitSet;

public class BloomFilter {
    private final BitSet bitArray;
    private final int bitArraySize;
    private final int hashFunctionCount;
    private final int expectedEntries;
    
    public BloomFilter(int expectedEntries, double falsePositiveRate) {
        this.expectedEntries = expectedEntries;
        this.bitArraySize = optimalBitArraySize(expectedEntries, falsePositiveRate);
        this.hashFunctionCount = optimalHashFunctionCount(bitArraySize, expectedEntries);
        this.bitArray = new BitSet(bitArraySize);
    }
    
    // 添加元素
    public void add(String element) {
        for (int i = 0; i < hashFunctionCount; i++) {
            int hash = hash(element, i);
            bitArray.set(hash);
        }
    }
    
    // 检查元素是否可能存在
    public boolean mightContain(String element) {
        for (int i = 0; i < hashFunctionCount; i++) {
            int hash = hash(element, i);
            if (!bitArray.get(hash)) {
                return false; // 确定不存在
            }
        }
        return true; // 可能存在
    }
}
```

### 哈希函数设计

我们使用**双重哈希 (Double Hashing)** 技术生成多个哈希值：

```java
private int hash(String element, int hashNumber) {
    // 使用双重哈希避免计算多个哈希函数
    int hash1 = element.hashCode();
    int hash2 = hash1 >>> 16; // 高16位作为第二个哈希值
    
    // 组合两个哈希值生成第i个哈希值
    int combinedHash = hash1 + hashNumber * hash2;
    
    // 确保结果为正数并在范围内
    return Math.abs(combinedHash % bitArraySize);
}
```

**双重哈希的优势**:
1. **性能**: 只需计算两个基础哈希值
2. **分布**: 提供良好的哈希分布
3. **简单**: 实现简单，调试容易

### 序列化支持

```java
// 序列化为字符串
public String serialize() {
    StringBuilder sb = new StringBuilder();
    sb.append(bitArraySize).append(",");
    sb.append(hashFunctionCount).append(",");
    sb.append(expectedEntries).append(",");
    
    // 将BitSet转换为十六进制字符串
    byte[] bytes = bitArray.toByteArray();
    for (byte b : bytes) {
        sb.append(String.format("%02x", b));
    }
    
    return sb.toString();
}

// 从字符串反序列化
public static BloomFilter deserialize(String data) {
    if (data == null || data.trim().isEmpty()) {
        return null;
    }
    
    String[] parts = data.split(",", 4);
    if (parts.length != 4) {
        return null;
    }
    
    try {
        int bitArraySize = Integer.parseInt(parts[0]);
        int hashFunctionCount = Integer.parseInt(parts[1]);
        int expectedEntries = Integer.parseInt(parts[2]);
        String hexData = parts[3];
        
        // 重建布隆过滤器
        BloomFilter filter = new BloomFilter(expectedEntries, 0.01);
        
        // 恢复BitSet
        if (!hexData.isEmpty()) {
            byte[] bytes = hexStringToByteArray(hexData);
            filter.bitArray = BitSet.valueOf(bytes);
        }
        
        return filter;
    } catch (Exception e) {
        return null;
    }
}

private static byte[] hexStringToByteArray(String hex) {
    int len = hex.length();
    byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
        data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                + Character.digit(hex.charAt(i + 1), 16));
    }
    return data;
}
```

## 性能分析和优化

### 1. 内存使用分析

```java
public class BloomFilterMemoryAnalysis {
    
    public static void analyzeMemoryUsage(int expectedEntries, double fpp) {
        int bitArraySize = BloomFilterMath.optimalBitArraySize(expectedEntries, fpp);
        int hashFunctionCount = BloomFilterMath.optimalHashFunctionCount(bitArraySize, expectedEntries);
        
        // BitSet内存占用 (大约 bitArraySize / 8 字节)
        long bitSetMemory = (bitArraySize + 7) / 8;
        
        // 对象开销 (大约 24字节 + 字段引用)
        long objectOverhead = 24 + 16;
        
        long totalMemory = bitSetMemory + objectOverhead;
        
        System.out.printf("布隆过滤器内存分析:%n");
        System.out.printf("预期条目: %,d%n", expectedEntries);
        System.out.printf("假阳性率: %.3f%n", fpp);
        System.out.printf("位数组大小: %,d bits%n", bitArraySize);
        System.out.printf("哈希函数数量: %d%n", hashFunctionCount);
        System.out.printf("内存占用: %,d bytes (%.2f KB)%n", totalMemory, totalMemory / 1024.0);
        System.out.printf("每条目内存: %.2f bytes%n", (double) totalMemory / expectedEntries);
    }
    
    public static void main(String[] args) {
        // 不同规模的内存分析
        analyzeMemoryUsage(1_000, 0.01);
        System.out.println();
        analyzeMemoryUsage(10_000, 0.01);
        System.out.println();
        analyzeMemoryUsage(100_000, 0.01);
    }
}
```

### 2. 性能基准测试

```java
public class BloomFilterBenchmark {
    
    public static void main(String[] args) {
        benchmarkDifferentSizes();
        benchmarkDifferentFalsePositiveRates();
        benchmarkHashFunctions();
    }
    
    private static void benchmarkDifferentSizes() {
        System.out.println("=== 不同大小的性能测试 ===");
        
        int[] sizes = {1_000, 10_000, 100_000, 1_000_000};
        
        for (int size : sizes) {
            BloomFilter filter = new BloomFilter(size, 0.01);
            
            // 测试插入性能
            long startTime = System.nanoTime();
            for (int i = 0; i < size; i++) {
                filter.add("key_" + i);
            }
            long insertTime = System.nanoTime() - startTime;
            
            // 测试查询性能
            startTime = System.nanoTime();
            int hitCount = 0;
            for (int i = 0; i < size; i++) {
                if (filter.mightContain("key_" + i)) {
                    hitCount++;
                }
            }
            long queryTime = System.nanoTime() - startTime;
            
            // 测试假阳性率
            int falsePositives = 0;
            for (int i = size; i < size + 1000; i++) {
                if (filter.mightContain("key_" + i)) {
                    falsePositives++;
                }
            }
            double actualFPP = falsePositives / 1000.0;
            
            System.out.printf("大小: %,7d | 插入: %,8.2f ms | 查询: %,8.2f ms | 实际FPP: %.3f%n",
                    size,
                    insertTime / 1_000_000.0,
                    queryTime / 1_000_000.0,
                    actualFPP);
        }
    }
    
    private static void benchmarkDifferentFalsePositiveRates() {
        System.out.println("\n=== 不同假阳性率的性能测试 ===");
        
        double[] fpps = {0.001, 0.01, 0.05, 0.1};
        int size = 10_000;
        
        for (double fpp : fpps) {
            BloomFilter filter = new BloomFilter(size, fpp);
            
            // 填充数据
            for (int i = 0; i < size; i++) {
                filter.add("key_" + i);
            }
            
            // 测试查询性能
            long startTime = System.nanoTime();
            for (int i = 0; i < 1000; i++) {
                filter.mightContain("key_" + i);
            }
            long queryTime = System.nanoTime() - startTime;
            
            // 测试实际假阳性率
            int falsePositives = 0;
            for (int i = size; i < size + 1000; i++) {
                if (filter.mightContain("key_" + i)) {
                    falsePositives++;
                }
            }
            double actualFPP = falsePositives / 1000.0;
            
            System.out.printf("期望FPP: %.3f | 查询: %,6.2f μs | 实际FPP: %.3f | 内存: %,d bytes%n",
                    fpp,
                    queryTime / 1000.0,
                    actualFPP,
                    estimateMemoryUsage(filter));
        }
    }
    
    private static long estimateMemoryUsage(BloomFilter filter) {
        // 简单估算，实际实现中可以添加getMemoryUsage()方法
        return filter.getBitArraySize() / 8 + 40; // BitSet + 对象开销
    }
}
```

### 3. 哈希函数优化

```java
public class OptimizedHashFunctions {
    
    // Murmur哈希函数 - 更好的分布特性
    public static class MurmurHashBloomFilter extends BloomFilter {
        
        @Override
        protected int hash(String element, int hashNumber) {
            byte[] data = element.getBytes(StandardCharsets.UTF_8);
            
            // 使用Murmur3哈希的简化版本
            int hash1 = murmurHash3(data, hashNumber);
            int hash2 = murmurHash3(data, hashNumber + 1);
            
            return Math.abs((hash1 + hashNumber * hash2) % getBitArraySize());
        }
        
        private int murmurHash3(byte[] data, int seed) {
            final int c1 = 0xcc9e2d51;
            final int c2 = 0x1b873593;
            final int r1 = 15;
            final int r2 = 13;
            final int m = 5;
            final int n = 0xe6546b64;
            
            int hash = seed;
            
            for (int i = 0; i < data.length - 3; i += 4) {
                int k = (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8) |
                       ((data[i + 2] & 0xff) << 16) | ((data[i + 3] & 0xff) << 24);
                
                k *= c1;
                k = Integer.rotateLeft(k, r1);
                k *= c2;
                
                hash ^= k;
                hash = Integer.rotateLeft(hash, r2) * m + n;
            }
            
            // 处理剩余字节
            int remainingBytes = data.length % 4;
            if (remainingBytes > 0) {
                int k = 0;
                for (int i = data.length - remainingBytes; i < data.length; i++) {
                    k |= (data[i] & 0xff) << ((i % 4) * 8);
                }
                k *= c1;
                k = Integer.rotateLeft(k, r1);
                k *= c2;
                hash ^= k;
            }
            
            hash ^= data.length;
            hash ^= (hash >>> 16);
            hash *= 0x85ebca6b;
            hash ^= (hash >>> 13);
            hash *= 0xc2b2ae35;
            hash ^= (hash >>> 16);
            
            return hash;
        }
    }
}
```

## 实际应用场景

### 1. LSM Tree集成

```java
public class LSMTreeWithBloomFilter {
    
    public static void demonstrateBloomFilterBenefit() throws IOException {
        // 创建测试数据
        List<KeyValue> testData = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            testData.add(new KeyValue("key_" + i, "value_" + i));
        }
        
        // 创建带布隆过滤器的SSTable
        SSTable ssTableWithBloom = new SSTable("test_with_bloom.db", testData);
        
        // 创建不带布隆过滤器的SSTable (模拟)
        SSTableWithoutBloom ssTableWithoutBloom = new SSTableWithoutBloom("test_without_bloom.db", testData);
        
        // 测试查询性能 - 查询不存在的键
        List<String> nonExistentKeys = new ArrayList<>();
        for (int i = 20_000; i < 21_000; i++) {
            nonExistentKeys.add("key_" + i);
        }
        
        // 带布隆过滤器的查询
        long startTime = System.currentTimeMillis();
        int bloomFilterQueries = 0;
        for (String key : nonExistentKeys) {
            String result = ssTableWithBloom.get(key);
            bloomFilterQueries++;
        }
        long bloomFilterTime = System.currentTimeMillis() - startTime;
        
        // 不带布隆过滤器的查询
        startTime = System.currentTimeMillis();
        int directQueries = 0;
        for (String key : nonExistentKeys) {
            String result = ssTableWithoutBloom.get(key);
            directQueries++;
        }
        long directTime = System.currentTimeMillis() - startTime;
        
        System.out.printf("查询1000个不存在的键:%n");
        System.out.printf("带布隆过滤器: %d ms%n", bloomFilterTime);
        System.out.printf("不带布隆过滤器: %d ms%n", directTime);
        System.out.printf("性能提升: %.1fx%n", (double) directTime / bloomFilterTime);
    }
}
```

### 2. 分布式系统中的应用

```java
public class DistributedBloomFilter {
    
    // 网络传输优化
    public static class CompressedBloomFilter extends BloomFilter {
        
        public byte[] serializeCompressed() {
            // 使用GZIP压缩序列化数据
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
                
                String serialized = serialize();
                gzos.write(serialized.getBytes(StandardCharsets.UTF_8));
                gzos.finish();
                
                return baos.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException("压缩失败", e);
            }
        }
        
        public static CompressedBloomFilter deserializeCompressed(byte[] compressed) {
            try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
                 GZIPInputStream gzis = new GZIPInputStream(bais);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(gzis, StandardCharsets.UTF_8))) {
                
                String serialized = reader.readLine();
                return (CompressedBloomFilter) deserialize(serialized);
            } catch (IOException e) {
                throw new RuntimeException("解压失败", e);
            }
        }
    }
    
    // 分片布隆过滤器
    public static class ShardedBloomFilter {
        private final BloomFilter[] shards;
        private final int shardCount;
        
        public ShardedBloomFilter(int shardCount, int expectedEntriesPerShard, double fpp) {
            this.shardCount = shardCount;
            this.shards = new BloomFilter[shardCount];
            
            for (int i = 0; i < shardCount; i++) {
                shards[i] = new BloomFilter(expectedEntriesPerShard, fpp);
            }
        }
        
        private int getShardIndex(String element) {
            return Math.abs(element.hashCode() % shardCount);
        }
        
        public void add(String element) {
            int shardIndex = getShardIndex(element);
            shards[shardIndex].add(element);
        }
        
        public boolean mightContain(String element) {
            int shardIndex = getShardIndex(element);
            return shards[shardIndex].mightContain(element);
        }
        
        // 合并操作 - 用于分布式环境
        public void merge(ShardedBloomFilter other) {
            if (other.shardCount != this.shardCount) {
                throw new IllegalArgumentException("分片数量不匹配");
            }
            
            for (int i = 0; i < shardCount; i++) {
                this.shards[i].union(other.shards[i]);
            }
        }
    }
}
```

### 3. 缓存系统集成

```java
public class BloomFilterCache {
    private final BloomFilter cacheFilter;
    private final Map<String, String> actualCache;
    private final AtomicLong bloomFilterSaves = new AtomicLong(0);
    
    public BloomFilterCache(int expectedSize, double fpp) {
        this.cacheFilter = new BloomFilter(expectedSize, fpp);
        this.actualCache = new ConcurrentHashMap<>();
    }
    
    public void put(String key, String value) {
        actualCache.put(key, value);
        cacheFilter.add(key);
    }
    
    public String get(String key) {
        // 先检查布隆过滤器
        if (!cacheFilter.mightContain(key)) {
            bloomFilterSaves.incrementAndGet();
            return null; // 确定不存在
        }
        
        // 布隆过滤器说可能存在，检查实际缓存
        return actualCache.get(key);
    }
    
    public void remove(String key) {
        actualCache.remove(key);
        // 注意：布隆过滤器不支持删除，这是一个权衡
    }
    
    public long getBloomFilterSaves() {
        return bloomFilterSaves.get();
    }
    
    public double getBloomFilterEfficiency() {
        long totalGets = bloomFilterSaves.get() + actualCache.size();
        return totalGets > 0 ? (double) bloomFilterSaves.get() / totalGets : 0.0;
    }
}
```

## 高级优化技术

### 1. 计数布隆过滤器

```java
public class CountingBloomFilter {
    private final int[] counters;
    private final int bitArraySize;
    private final int hashFunctionCount;
    
    public CountingBloomFilter(int expectedEntries, double fpp) {
        this.bitArraySize = BloomFilterMath.optimalBitArraySize(expectedEntries, fpp);
        this.hashFunctionCount = BloomFilterMath.optimalHashFunctionCount(bitArraySize, expectedEntries);
        this.counters = new int[bitArraySize];
    }
    
    public void add(String element) {
        for (int i = 0; i < hashFunctionCount; i++) {
            int hash = hash(element, i);
            counters[hash]++;
        }
    }
    
    public void remove(String element) {
        // 检查是否可以安全删除
        if (!mightContain(element)) {
            return;
        }
        
        for (int i = 0; i < hashFunctionCount; i++) {
            int hash = hash(element, i);
            if (counters[hash] > 0) {
                counters[hash]--;
            }
        }
    }
    
    public boolean mightContain(String element) {
        for (int i = 0; i < hashFunctionCount; i++) {
            int hash = hash(element, i);
            if (counters[hash] == 0) {
                return false;
            }
        }
        return true;
    }
    
    // 获取元素的估计计数
    public int getEstimatedCount(String element) {
        int minCount = Integer.MAX_VALUE;
        
        for (int i = 0; i < hashFunctionCount; i++) {
            int hash = hash(element, i);
            minCount = Math.min(minCount, counters[hash]);
        }
        
        return minCount == Integer.MAX_VALUE ? 0 : minCount;
    }
}
```

### 2. 自适应布隆过滤器

```java
public class AdaptiveBloomFilter {
    private BloomFilter primaryFilter;
    private BloomFilter secondaryFilter;
    private final double targetFpp;
    private final AtomicLong insertCount = new AtomicLong(0);
    private final AtomicLong falsePositiveCount = new AtomicLong(0);
    
    public AdaptiveBloomFilter(int initialCapacity, double targetFpp) {
        this.targetFpp = targetFpp;
        this.primaryFilter = new BloomFilter(initialCapacity, targetFpp);
    }
    
    public void add(String element) {
        primaryFilter.add(element);
        if (secondaryFilter != null) {
            secondaryFilter.add(element);
        }
        
        long count = insertCount.incrementAndGet();
        
        // 定期评估性能并可能重建过滤器
        if (count % 10000 == 0) {
            evaluateAndOptimize();
        }
    }
    
    public boolean mightContain(String element) {
        boolean primaryResult = primaryFilter.mightContain(element);
        
        if (!primaryResult) {
            return false;
        }
        
        // 如果有次级过滤器，也要检查
        if (secondaryFilter != null) {
            return secondaryFilter.mightContain(element);
        }
        
        return true;
    }
    
    private void evaluateAndOptimize() {
        double actualFpp = calculateActualFpp();
        
        if (actualFpp > targetFpp * 2) {
            // 假阳性率过高，重建过滤器
            rebuildWithLargerCapacity();
        }
    }
    
    private double calculateActualFpp() {
        long total = insertCount.get();
        long falsePositives = falsePositiveCount.get();
        return total > 0 ? (double) falsePositives / total : 0.0;
    }
    
    private void rebuildWithLargerCapacity() {
        int newCapacity = (int) (insertCount.get() * 1.5);
        secondaryFilter = new BloomFilter(newCapacity, targetFpp);
        // 在实际应用中，这里需要重新插入所有元素
    }
}
```

## 常见问题和解决方案

### 1. 假阳性率过高

**问题**: 实际假阳性率超过预期

**解决方案**:
```java
public class BloomFilterDiagnostics {
    
    public static void diagnoseHighFalsePositiveRate(BloomFilter filter, Set<String> actualElements) {
        // 测试1000个不存在的元素
        int falsePositives = 0;
        for (int i = 0; i < 1000; i++) {
            String testKey = "non_existent_" + i;
            if (filter.mightContain(testKey) && !actualElements.contains(testKey)) {
                falsePositives++;
            }
        }
        
        double actualFpp = falsePositives / 1000.0;
        System.out.printf("实际假阳性率: %.3f%n", actualFpp);
        
        if (actualFpp > 0.05) {
            System.out.println("建议:");
            System.out.println("1. 增加位数组大小");
            System.out.println("2. 检查哈希函数质量");
            System.out.println("3. 验证插入的元素数量是否超过预期");
        }
    }
}
```

### 2. 内存占用过大

**解决方案**:
```java
public class MemoryOptimizedBloomFilter {
    
    // 使用压缩位数组
    private static class CompressedBitSet {
        private final byte[] data;
        private final int size;
        
        public CompressedBitSet(int size) {
            this.size = size;
            this.data = new byte[(size + 7) / 8];
        }
        
        public void set(int index) {
            int byteIndex = index / 8;
            int bitIndex = index % 8;
            data[byteIndex] |= (1 << bitIndex);
        }
        
        public boolean get(int index) {
            int byteIndex = index / 8;
            int bitIndex = index % 8;
            return (data[byteIndex] & (1 << bitIndex)) != 0;
        }
        
        public int getMemoryUsage() {
            return data.length + 12; // 数组 + 对象开销
        }
    }
}
```

### 3. 并发访问问题

**解决方案**:
```java
public class ConcurrentBloomFilter {
    private final BloomFilter filter;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();
    
    public ConcurrentBloomFilter(int expectedEntries, double fpp) {
        this.filter = new BloomFilter(expectedEntries, fpp);
    }
    
    public void add(String element) {
        writeLock.lock();
        try {
            filter.add(element);
        } finally {
            writeLock.unlock();
        }
    }
    
    public boolean mightContain(String element) {
        readLock.lock();
        try {
            return filter.mightContain(element);
        } finally {
            readLock.unlock();
        }
    }
}
```

## 小结

布隆过滤器是LSM Tree性能优化的关键组件：

1. **空间效率**: 极小的内存占用
2. **时间效率**: O(k)的查询时间
3. **假阳性权衡**: 接受假阳性换取性能提升
4. **广泛应用**: 缓存、数据库、网络等场景

## 下一步学习

现在你已经理解了布隆过滤器的工作原理，接下来我们将学习WAL写前日志：

继续阅读：[第6章：WAL 写前日志](06-wal-logging.md)

---

## 思考题

1. 为什么布隆过滤器不能支持删除操作？
2. 如何选择最优的假阳性率？
3. 在什么情况下布隆过滤器的性能提升不明显？

**下一章预告**: 我们将深入学习WAL的设计原理、恢复机制和性能优化。 