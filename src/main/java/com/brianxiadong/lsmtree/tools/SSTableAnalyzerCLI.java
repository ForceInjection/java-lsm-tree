package com.brianxiadong.lsmtree.tools;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Scanner;

/**
 * SSTable 分析工具命令行界面
 * 
 * 提供多种分析SSTable文件的功能：
 * - 单文件分析：分析单个SSTable文件的结构和内容
 * - 文件比较：比较两个SSTable文件的差异
 * - 数据导出：将SSTable数据导出为JSON格式
 * - 批量分析：批量分析目录中的所有SSTable文件
 * - 交互模式：提供交互式命令行界面
 * 
 * @author Brian Xia Dong
 * @version 1.0
 */
public class SSTableAnalyzerCLI {
    
    /**
     * 程序入口点
     * 根据命令行参数分发到相应的处理方法
     * 
     * @param args 命令行参数，第一个参数为命令类型
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            showHelp();
            return;
        }
        
        String command = args[0];
        
        // 根据命令类型分发处理
        switch (command.toLowerCase()) {
            case "analyze":
            case "a":
                handleAnalyze(args);
                break;
            case "compare":
            case "c":
                handleCompare(args);
                break;
            case "export":
            case "e":
                handleExport(args);
                break;
            case "batch":
            case "b":
                handleBatch(args);
                break;
            case "interactive":
            case "i":
                handleInteractive();
                break;
            case "help":
            case "h":
            case "--help":
                showHelp();
                break;
            default:
                System.err.println("未知命令: " + command);
                showHelp();
        }
    }
    
    /**
     * 处理分析命令
     * 分析单个SSTable文件并显示统计信息
     * 
     * @param args 命令行参数，包含文件路径和可选参数
     */
    private static void handleAnalyze(String[] args) {
        if (args.length < 2) {
            System.err.println("用法: analyze <文件路径> [--show-data] [--max-entries=N]");
            return;
        }
        
        String filePath = args[1];
        boolean showData = Arrays.asList(args).contains("--show-data");
        int maxEntries = 10; // 默认显示10条
        
        // 解析 max-entries 参数
        for (String arg : args) {
            if (arg.startsWith("--max-entries=")) {
                try {
                    maxEntries = Integer.parseInt(arg.substring("--max-entries=".length()));
                } catch (NumberFormatException e) {
                    System.err.println("无效的 max-entries 值: " + arg);
                    return;
                }
            }
        }
        
        // 执行分析并显示结果
        SSTableAnalyzer.AnalysisResult result = SSTableAnalyzer.analyzeFile(filePath);
        SSTableAnalyzer.printAnalysisResult(result);
        
        // 如果需要显示数据内容
        if (showData && result.isValid()) {
            SSTableAnalyzer.printDataContent(result, maxEntries);
        }
    }
    
    /**
     * 处理比较命令
     * 比较两个SSTable文件的差异
     * 
     * @param args 命令行参数，包含两个文件路径
     */
    private static void handleCompare(String[] args) {
        if (args.length < 3) {
            System.err.println("用法: compare <文件1路径> <文件2路径>");
            return;
        }
        
        String file1 = args[1];
        String file2 = args[2];
        
        SSTableAnalyzer.compareFiles(file1, file2);
    }
    
    /**
     * 处理导出命令
     * 将SSTable数据导出为JSON格式
     * 
     * @param args 命令行参数，包含输入文件路径和输出文件路径
     */
    private static void handleExport(String[] args) {
        if (args.length < 3) {
            System.err.println("用法: export <输入文件路径> <输出JSON路径>");
            return;
        }
        
        String inputFile = args[1];
        String outputFile = args[2];
        
        // 先分析文件确保有效性
        SSTableAnalyzer.AnalysisResult result = SSTableAnalyzer.analyzeFile(inputFile);
        
        if (!result.isValid()) {
            System.err.println("输入文件无效: " + result.getErrorMessage());
            return;
        }
        
        // 执行导出操作
        try {
            SSTableAnalyzer.exportToJson(result, outputFile);
            System.out.println("数据已导出到: " + outputFile);
        } catch (IOException e) {
            System.err.println("导出失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理批量分析命令
     * 批量分析目录中的所有SSTable文件
     * 
     * @param args 命令行参数，包含目录路径
     */
    private static void handleBatch(String[] args) {
        if (args.length < 2) {
            System.err.println("用法: batch <目录路径>");
            return;
        }
        
        String dirPath = args[1];
        File dir = new File(dirPath);
        
        // 验证目录存在性
        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("目录不存在: " + dirPath);
            return;
        }
        
        // 查找SSTable文件（.db 或 .sst 扩展名）
        File[] files = dir.listFiles((d, name) -> name.endsWith(".db") || name.endsWith(".sst"));
        
        if (files == null || files.length == 0) {
            System.out.println("目录中没有找到 SSTable 文件 (.db 或 .sst)");
            return;
        }
        
        // 显示批量分析结果表头
        System.out.println("=== 批量分析结果 ===");
        System.out.printf("%-30s %-10s %-10s %-10s %-10s %-10s%n", 
                         "文件名", "大小", "条目数", "活跃", "删除", "状态");
        System.out.println("------------------------------------------------------------------------------------------");
        
        // 逐个分析文件并显示结果
        for (File file : files) {
            SSTableAnalyzer.AnalysisResult result = SSTableAnalyzer.analyzeFile(file.getAbsolutePath());
            
            // 处理文件名长度，避免表格错位
            String fileName = file.getName();
            if (fileName.length() > 28) {
                fileName = fileName.substring(0, 25) + "...";
            }
            
            // 格式化输出分析结果
            System.out.printf("%-30s %-10s %-10d %-10d %-10d %-10s%n",
                             fileName,
                             formatFileSize(result.getFileSize()),
                             result.getEntryCount(),
                             result.getActiveCount(),
                             result.getDeletedCount(),
                             result.isValid() ? "有效" : "无效");
        }
    }
    
    /**
     * 处理交互模式
     * 提供交互式命令行界面，允许用户连续执行多个命令
     */
    private static void handleInteractive() {
        System.out.println("=== SSTable 分析工具 - 交互模式 ===");
        System.out.println("输入 'help' 查看可用命令，输入 'quit' 退出");
        
        // 使用 try-with-resources 确保 Scanner 资源正确关闭
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("> ");
                String input = scanner.nextLine().trim();
                
                // 跳过空输入
                if (input.isEmpty()) {
                    continue;
                }
                
                // 解析用户输入的命令和参数
                String[] parts = input.split("\\s+");
                String command = parts[0].toLowerCase();
                
                // 处理交互式命令
                switch (command) {
                    case "quit":
                    case "exit":
                        System.out.println("再见！");
                        return;
                        
                    case "help":
                        showInteractiveHelp();
                        break;
                        
                    case "analyze":
                        if (parts.length < 2) {
                            System.out.println("用法: analyze <文件路径>");
                            break;
                        }
                        SSTableAnalyzer.AnalysisResult result = SSTableAnalyzer.analyzeFile(parts[1]);
                        SSTableAnalyzer.printAnalysisResult(result);
                        break;
                        
                    case "show":
                        if (parts.length < 2) {
                            System.out.println("用法: show <文件路径> [条目数]");
                            break;
                        }
                        // 解析显示条目数参数，默认为10
                        int maxEntries = parts.length > 2 ? Integer.parseInt(parts[2]) : 10;
                        SSTableAnalyzer.AnalysisResult showResult = SSTableAnalyzer.analyzeFile(parts[1]);
                        if (showResult.isValid()) {
                            SSTableAnalyzer.printDataContent(showResult, maxEntries);
                        } else {
                            System.out.println("文件无效: " + showResult.getErrorMessage());
                        }
                        break;
                        
                    case "compare":
                        if (parts.length < 3) {
                            System.out.println("用法: compare <文件1> <文件2>");
                            break;
                        }
                        SSTableAnalyzer.compareFiles(parts[1], parts[2]);
                        break;
                        
                    default:
                        System.out.println("未知命令: " + command + "。输入 'help' 查看可用命令。");
                }
            }
        }
    }
    
    /**
     * 显示主帮助信息
     * 包含所有可用命令的详细说明和使用示例
     */
    private static void showHelp() {
        System.out.println("SSTable 分析工具 v1.0");
        System.out.println();
        System.out.println("用法: java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzerCLI <命令> [选项]");
        System.out.println();
        System.out.println("命令:");
        System.out.println("  analyze, a    <文件路径> [--show-data] [--max-entries=N]");
        System.out.println("                分析单个 SSTable 文件");
        System.out.println("                --show-data: 显示数据内容");
        System.out.println("                --max-entries=N: 最多显示 N 条记录 (默认: 10)");
        System.out.println();
        System.out.println("  compare, c    <文件1> <文件2>");
        System.out.println("                比较两个 SSTable 文件");
        System.out.println();
        System.out.println("  export, e     <输入文件> <输出JSON文件>");
        System.out.println("                将 SSTable 数据导出为 JSON 格式");
        System.out.println();
        System.out.println("  batch, b      <目录路径>");
        System.out.println("                批量分析目录中的所有 SSTable 文件");
        System.out.println();
        System.out.println("  interactive, i");
        System.out.println("                进入交互模式");
        System.out.println();
        System.out.println("  help, h       显示此帮助信息");
        System.out.println();
        System.out.println("示例:");
        System.out.println("  java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzerCLI analyze data/level0/table1.db");
        System.out.println("  java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzerCLI analyze data/level0/table1.db --show-data --max-entries=20");
        System.out.println("  java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzerCLI compare file1.db file2.db");
        System.out.println("  java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzerCLI batch data/");
        System.out.println("  java -cp target/classes com.brianxiadong.lsmtree.tools.SSTableAnalyzerCLI interactive");
    }
    
    /**
     * 显示交互模式帮助信息
     * 列出交互模式下可用的命令
     */
    private static void showInteractiveHelp() {
        System.out.println("交互模式命令:");
        System.out.println("  analyze <文件路径>           - 分析文件");
        System.out.println("  show <文件路径> [条目数]     - 显示文件内容");
        System.out.println("  compare <文件1> <文件2>      - 比较两个文件");
        System.out.println("  help                         - 显示此帮助");
        System.out.println("  quit, exit                   - 退出程序");
    }
    
    /**
     * 格式化文件大小显示
     * 将字节数转换为人类可读的格式（B, K, M, G）
     * 
     * @param bytes 文件大小（字节）
     * @return 格式化后的文件大小字符串
     */
    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fK", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1fM", bytes / (1024.0 * 1024));
        return String.format("%.1fG", bytes / (1024.0 * 1024 * 1024));
    }
}