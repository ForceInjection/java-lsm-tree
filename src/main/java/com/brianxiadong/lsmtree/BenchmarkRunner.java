package com.brianxiadong.lsmtree;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * LSM Tree 基准测试运行器
 * 可以直接运行，不依赖JUnit
 */
public class BenchmarkRunner {

    public static void main(String[] args) {
        System.out.println("====================================");
        System.out.println("     LSM Tree 性能基准测试");
        System.out.println("====================================");

        BenchmarkRunner runner = new BenchmarkRunner();

        try {
            runner.runAllBenchmarks();
        } catch (Exception e) {
            System.err.println("基准测试失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("====================================");
        System.out.println("         基准测试完成");
        System.out.println("====================================");
    }

    private void runAllBenchmarks() throws IOException {
        // 运行各种基准测试
        benchmarkSequentialWrites();
        System.out.println();

        benchmarkRandomWrites();
        System.out.println();

        benchmarkReads();
        System.out.println();

        benchmarkMixedWorkload();
        System.out.println();

        benchmarkWriteLatency();
        System.out.println();

        benchmarkMemTableFlushImpact();
    }

    private void benchmarkSequentialWrites() throws IOException {
        System.out.println("=== 顺序写入性能测试 ===");

        String testDir = "benchmark_seq_" + System.currentTimeMillis();
        LSMTree lsmTree = new LSMTree(testDir, 10000);

        try {
            int[] testSizes = { 1000, 5000, 10000, 20000 };

            for (int size : testSizes) {
                long startTime = System.nanoTime();

                for (int i = 0; i < size; i++) {
                    lsmTree.put("key_" + String.format("%08d", i), "value_" + i);
                }

                long endTime = System.nanoTime();
                double durationMs = (endTime - startTime) / 1_000_000.0;
                double throughput = size / (durationMs / 1000.0);

                System.out.printf("数据量: %,6d | 耗时: %,8.2f ms | 吞吐量: %,8.0f ops/sec%n",
                        size, durationMs, throughput);
            }
        } finally {
            lsmTree.close();
            deleteDirectory(new File(testDir));
        }
    }

    private void benchmarkRandomWrites() throws IOException {
        System.out.println("=== 随机写入性能测试 ===");

        String testDir = "benchmark_rand_" + System.currentTimeMillis();
        LSMTree lsmTree = new LSMTree(testDir, 10000);
        Random random = new Random(42);

        try {
            int[] testSizes = { 1000, 5000, 10000, 20000 };

            for (int size : testSizes) {
                // 生成随机键
                List<String> keys = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    keys.add("key_" + random.nextInt(size * 2));
                }

                long startTime = System.nanoTime();

                for (int i = 0; i < size; i++) {
                    lsmTree.put(keys.get(i), "value_" + i);
                }

                long endTime = System.nanoTime();
                double durationMs = (endTime - startTime) / 1_000_000.0;
                double throughput = size / (durationMs / 1000.0);

                System.out.printf("数据量: %,6d | 耗时: %,8.2f ms | 吞吐量: %,8.0f ops/sec%n",
                        size, durationMs, throughput);
            }
        } finally {
            lsmTree.close();
            deleteDirectory(new File(testDir));
        }
    }

    private void benchmarkReads() throws IOException {
        System.out.println("=== 读取性能测试 ===");

        String testDir = "benchmark_read_" + System.currentTimeMillis();
        LSMTree lsmTree = new LSMTree(testDir, 10000);
        Random random = new Random(42);

        try {
            // 先写入测试数据
            int dataSize = 15000;
            List<String> keys = new ArrayList<>();

            System.out.println("准备测试数据...");
            for (int i = 0; i < dataSize; i++) {
                String key = "read_key_" + i;
                keys.add(key);
                lsmTree.put(key, "value_" + i);
            }

            // 强制刷盘，模拟真实场景
            lsmTree.flush();

            int[] readSizes = { 1000, 5000, 10000 };

            for (int readSize : readSizes) {
                // 随机选择要读取的键
                Collections.shuffle(keys, random);
                List<String> readKeys = keys.subList(0, readSize);

                long startTime = System.nanoTime();
                int hitCount = 0;

                for (String key : readKeys) {
                    String value = lsmTree.get(key);
                    if (value != null) {
                        hitCount++;
                    }
                }

                long endTime = System.nanoTime();
                double durationMs = (endTime - startTime) / 1_000_000.0;
                double throughput = readSize / (durationMs / 1000.0);
                double hitRate = (double) hitCount / readSize * 100;

                System.out.printf("读取量: %,6d | 耗时: %,8.2f ms | 吞吐量: %,8.0f ops/sec | 命中率: %.1f%%%n",
                        readSize, durationMs, throughput, hitRate);
            }
        } finally {
            lsmTree.close();
            deleteDirectory(new File(testDir));
        }
    }

    private void benchmarkMixedWorkload() throws IOException {
        System.out.println("=== 混合工作负载测试 (70%读 + 30%写) ===");

        String testDir = "benchmark_mixed_" + System.currentTimeMillis();
        LSMTree lsmTree = new LSMTree(testDir, 10000);
        Random random = new Random(42);

        try {
            // 先准备一些基础数据
            int baseDataSize = 8000;
            for (int i = 0; i < baseDataSize; i++) {
                lsmTree.put("base_key_" + i, "base_value_" + i);
            }
            lsmTree.flush();

            int totalOps = 15000;
            long startTime = System.nanoTime();

            int reads = 0, writes = 0, hits = 0;

            for (int i = 0; i < totalOps; i++) {
                if (random.nextDouble() < 0.7) {
                    // 70% 读操作
                    String key = "base_key_" + random.nextInt(baseDataSize);
                    String value = lsmTree.get(key);
                    reads++;
                    if (value != null)
                        hits++;
                } else {
                    // 30% 写操作
                    String key = "mixed_key_" + i;
                    lsmTree.put(key, "mixed_value_" + i);
                    writes++;
                }
            }

            long endTime = System.nanoTime();
            double durationMs = (endTime - startTime) / 1_000_000.0;
            double throughput = totalOps / (durationMs / 1000.0);
            double hitRate = (double) hits / reads * 100;

            System.out.printf("总操作数: %,d | 读操作: %,d | 写操作: %,d%n", totalOps, reads, writes);
            System.out.printf("总耗时: %,8.2f ms | 整体吞吐量: %,8.0f ops/sec%n", durationMs, throughput);
            System.out.printf("读命中率: %.1f%% | 统计信息: %s%n", hitRate, lsmTree.getStats());
        } finally {
            lsmTree.close();
            deleteDirectory(new File(testDir));
        }
    }

    private void benchmarkWriteLatency() throws IOException {
        System.out.println("=== 写入延迟分布测试 ===");

        String testDir = "benchmark_latency_" + System.currentTimeMillis();
        LSMTree lsmTree = new LSMTree(testDir, 10000);

        try {
            int testCount = 5000;
            List<Long> latencies = new ArrayList<>();

            for (int i = 0; i < testCount; i++) {
                long startTime = System.nanoTime();
                lsmTree.put("latency_key_" + i, "latency_value_" + i);
                long endTime = System.nanoTime();

                latencies.add(endTime - startTime);
            }

            // 计算统计信息
            Collections.sort(latencies);

            long min = latencies.get(0);
            long max = latencies.get(latencies.size() - 1);
            long median = latencies.get(latencies.size() / 2);
            long p95 = latencies.get((int) (latencies.size() * 0.95));
            long p99 = latencies.get((int) (latencies.size() * 0.99));

            double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);

            System.out.printf("延迟统计 (微秒):%n");
            System.out.printf("最小值: %,8.1f | 最大值: %,8.1f | 平均值: %,8.1f%n",
                    min / 1000.0, max / 1000.0, avg / 1000.0);
            System.out.printf("中位数: %,8.1f | P95: %,8.1f | P99: %,8.1f%n",
                    median / 1000.0, p95 / 1000.0, p99 / 1000.0);
        } finally {
            lsmTree.close();
            deleteDirectory(new File(testDir));
        }
    }

    private void benchmarkMemTableFlushImpact() throws IOException {
        System.out.println("=== MemTable刷盘对性能的影响 ===");

        String testDir = "benchmark_flush_" + System.currentTimeMillis();

        // 使用小的MemTable来频繁触发刷盘
        LSMTree smallMemTableLSM = new LSMTree(testDir, 100);

        try {
            int testSize = 2000;

            // 测试会触发多次刷盘的性能
            long startTime = System.nanoTime();
            for (int i = 0; i < testSize; i++) {
                smallMemTableLSM.put("flush_key_" + i, "flush_value_" + i);
            }
            long endTime = System.nanoTime();

            double durationMs = (endTime - startTime) / 1_000_000.0;
            double throughput = testSize / (durationMs / 1000.0);

            System.out.printf("频繁刷盘场景 - 数据量: %,d | 耗时: %,8.2f ms | 吞吐量: %,8.0f ops/sec%n",
                    testSize, durationMs, throughput);
            System.out.println("统计信息: " + smallMemTableLSM.getStats());
        } finally {
            smallMemTableLSM.close();
            deleteDirectory(new File(testDir));
        }
    }

    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
}