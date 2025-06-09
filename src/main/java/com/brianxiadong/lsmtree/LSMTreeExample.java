package com.brianxiadong.lsmtree;

import java.io.IOException;

/**
 * LSM Tree 使用示例
 */
public class LSMTreeExample {
    public static void main(String[] args) {
        String dataDir = "lsm_data";

        try (LSMTree lsmTree = new LSMTree(dataDir, 1000)) {

            System.out.println("=== LSM Tree 基本操作示例 ===");

            // 插入数据
            lsmTree.put("user:1", "Alice");
            lsmTree.put("user:2", "Bob");
            lsmTree.put("user:3", "Charlie");

            // 查询数据
            System.out.println("user:1 = " + lsmTree.get("user:1"));
            System.out.println("user:2 = " + lsmTree.get("user:2"));
            System.out.println("user:3 = " + lsmTree.get("user:3"));

            // 更新数据
            lsmTree.put("user:1", "Alice Updated");
            System.out.println("更新后 user:1 = " + lsmTree.get("user:1"));

            // 删除数据
            lsmTree.delete("user:2");
            System.out.println("删除后 user:2 = " + lsmTree.get("user:2")); // 应该返回null

            System.out.println("\n=== 大量数据插入测试 ===");

            // 插入大量数据以触发MemTable刷盘和压缩
            long startTime = System.currentTimeMillis();
            for (int i = 0; i < 10000; i++) {
                lsmTree.put("key:" + i, "value:" + i);
            }
            long endTime = System.currentTimeMillis();
            System.out.println("插入10000条记录耗时: " + (endTime - startTime) + "ms");

            // 随机查询测试
            startTime = System.currentTimeMillis();
            for (int i = 0; i < 1000; i++) {
                int randomKey = (int) (Math.random() * 10000);
                String value = lsmTree.get("key:" + randomKey);
                if (value == null) {
                    System.out.println("未找到 key:" + randomKey);
                }
            }
            endTime = System.currentTimeMillis();
            System.out.println("随机查询1000次耗时: " + (endTime - startTime) + "ms");

            // 显示统计信息
            LSMTree.LSMTreeStats stats = lsmTree.getStats();
            System.out.println("\n=== LSM Tree 统计信息 ===");
            System.out.println(stats);

            // 强制刷盘
            System.out.println("\n=== 执行刷盘操作 ===");
            lsmTree.flush();
            System.out.println("刷盘完成");

            stats = lsmTree.getStats();
            System.out.println("刷盘后统计信息: " + stats);

        } catch (IOException e) {
            System.err.println("LSM Tree操作失败: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("未知错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}