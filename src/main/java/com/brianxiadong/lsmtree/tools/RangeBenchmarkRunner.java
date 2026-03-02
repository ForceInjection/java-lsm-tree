package com.brianxiadong.lsmtree.tools;

import com.brianxiadong.lsmtree.KeyValue;
import com.brianxiadong.lsmtree.LSMTree;

import java.io.File;
import java.util.Iterator;

/**
 * 范围查询基准测试工具
 * <p>
 * 用于评估 LSM Tree 在大量数据下的范围查询性能。
 * 包含数据准备、GC 触发和延迟统计。
 */
public class RangeBenchmarkRunner {
    
    /**
     * 运行基准测试
     * 
     * @param args 命令行参数：[0]=数据目录, [1]=数据条数
     * @throws Exception 如果发生错误
     */
    public static void main(String[] args) throws Exception {
        String dir = args.length > 0 ? args[0] : "./bench-range";
        int n = args.length > 1 ? Integer.parseInt(args[1]) : 100_000;
        
        System.out.println("开始基准测试...");
        System.out.println("数据目录: " + dir);
        System.out.println("数据条数: " + n);
        
        // 确保目录存在
        File d = new File(dir);
        if (!d.exists()) d.mkdirs();
        
        try (LSMTree tree = new LSMTree(dir, 2048)) {
            System.out.println("正在写入数据...");
            for (int i = 0; i < n; i++) {
                String k = String.format("k%06d", i);
                tree.put(k, "v" + i);
                if (i % 10000 == 0) System.out.print(".");
            }
            System.out.println("\n写入完成。");
            
            System.gc();
            System.out.println("开始范围查询...");
            
            long start = System.nanoTime();
            Iterator<KeyValue> it = tree.range("k000000", "k999999", true, true);
            int c = 0;
            while (it.hasNext()) { 
                it.next(); 
                c++; 
            }
            long end = System.nanoTime();
            
            double ms = (end - start) / 1_000_000.0;
            System.out.println("查询结果统计:");
            System.out.println("  条目数: " + c);
            System.out.println("  耗时: " + String.format("%.2f", ms) + " ms");
            System.out.println("  吞吐量: " + String.format("%.2f", c / (ms / 1000.0)) + " ops/sec");
        }
    }
}

