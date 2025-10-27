package com.brianxiadong.lsmtree.tools;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Scanner;

/**
 * WAL分析器命令行界面
 * 提供WAL文件分析、验证、导出等功能的命令行工具
 */
public class WALAnalyzerCLI {

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String command = args[0].toLowerCase();

        try {
            switch (command) {
                case "analyze":
                    handleAnalyze(Arrays.copyOfRange(args, 1, args.length));
                    break;
                case "validate":
                    handleValidate(Arrays.copyOfRange(args, 1, args.length));
                    break;
                case "export":
                    handleExport(Arrays.copyOfRange(args, 1, args.length));
                    break;
                case "interactive":
                    handleInteractive();
                    break;
                case "help":
                    printUsage();
                    break;
                default:
                    System.err.println("未知命令: " + command);
                    printUsage();
                    System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("执行命令时发生错误: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * 处理analyze命令
     */
    private static void handleAnalyze(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("错误: 请指定WAL文件路径");
            System.err.println("用法: analyze <wal_file> [--show-entries]");
            System.exit(1);
        }

        String walFile = args[0];
        boolean showEntries = false;

        // 解析选项
        for (int i = 1; i < args.length; i++) {
            if ("--show-entries".equals(args[i])) {
                showEntries = true;
            }
        }

        if (!new File(walFile).exists()) {
            System.err.println("错误: WAL文件不存在: " + walFile);
            System.exit(1);
        }

        WALAnalyzer.WALAnalysisResult result = WALAnalyzer.analyzeWAL(walFile);
        System.out.println(WALAnalyzer.formatAnalysisResult(result, showEntries));
    }

    /**
     * 处理validate命令
     */
    private static void handleValidate(String[] args) {
        if (args.length == 0) {
            System.err.println("错误: 请指定WAL文件路径");
            System.err.println("用法: validate <wal_file>");
            System.exit(1);
        }

        String walFile = args[0];

        if (!new File(walFile).exists()) {
            System.err.println("错误: WAL文件不存在: " + walFile);
            System.exit(1);
        }

        boolean isValid = WALAnalyzer.validateWAL(walFile);

        if (isValid) {
            System.out.println("✅ WAL文件验证通过: " + walFile);
        } else {
            System.out.println("❌ WAL文件验证失败: " + walFile);
            try {
                WALAnalyzer.WALAnalysisResult result = WALAnalyzer.analyzeWAL(walFile);
                if (!result.getErrors().isEmpty()) {
                    System.out.println("\n错误详情:");
                    for (String error : result.getErrors()) {
                        System.out.println("  " + error);
                    }
                }
            } catch (IOException e) {
                System.out.println("  无法读取文件: " + e.getMessage());
            }
            System.exit(1);
        }
    }

    /**
     * 处理export命令
     */
    private static void handleExport(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("错误: 请指定WAL文件路径和输出文件路径");
            System.err.println("用法: export <wal_file> <output_file>");
            System.exit(1);
        }

        String walFile = args[0];
        String outputFile = args[1];

        if (!new File(walFile).exists()) {
            System.err.println("错误: WAL文件不存在: " + walFile);
            System.exit(1);
        }

        WALAnalyzer.WALAnalysisResult result = WALAnalyzer.analyzeWAL(walFile);
        WALAnalyzer.exportToJSON(result, outputFile);

        System.out.println("✅ WAL数据已导出到: " + outputFile);
        System.out.println("总条目数: " + result.getStatistics().getTotalEntries());
    }

    /**
     * 处理interactive命令
     */
    private static void handleInteractive() {
        System.out.println(repeatString("=", 50));
        System.out.println("WAL分析器 - 交互模式");
        System.out.println(repeatString("=", 50));
        System.out.println("输入 'help' 查看可用命令，输入 'exit' 退出");
        System.out.println();

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("wal-analyzer> ");
                String input = scanner.nextLine().trim();

                if (input.isEmpty()) {
                    continue;
                }

                String[] parts = input.split("\\s+");
                String command = parts[0].toLowerCase();

                try {
                    switch (command) {
                        case "exit":
                        case "quit":
                            System.out.println("再见！");
                            return;
                        case "help":
                            printInteractiveHelp();
                            break;
                        case "analyze":
                            if (parts.length < 2) {
                                System.out.println("用法: analyze <wal_file> [--show-entries]");
                            } else {
                                handleAnalyze(Arrays.copyOfRange(parts, 1, parts.length));
                            }
                            break;
                        case "validate":
                            if (parts.length < 2) {
                                System.out.println("用法: validate <wal_file>");
                            } else {
                                handleValidate(Arrays.copyOfRange(parts, 1, parts.length));
                            }
                            break;
                        case "export":
                            if (parts.length < 3) {
                                System.out.println("用法: export <wal_file> <output_file>");
                            } else {
                                handleExport(Arrays.copyOfRange(parts, 1, parts.length));
                            }
                            break;
                        default:
                            System.out.println("未知命令: " + command + "。输入 'help' 查看可用命令。");
                    }
                } catch (Exception e) {
                    System.out.println("执行命令时发生错误: " + e.getMessage());
                }

                System.out.println();
            }
        }
    }

    /**
     * 打印使用说明
     */
    private static void printUsage() {
        System.out.println("WAL分析器 - LSM Tree写前日志分析工具");
        System.out.println();
        System.out.println("用法: java WALAnalyzerCLI <command> [options]");
        System.out.println();
        System.out.println("可用命令:");
        System.out.println("  analyze <wal_file> [--show-entries]");
        System.out.println("    分析WAL文件并显示统计信息");
        System.out.println("    --show-entries: 显示所有日志条目详情");
        System.out.println();
        System.out.println("  validate <wal_file>");
        System.out.println("    验证WAL文件格式的完整性");
        System.out.println();
        System.out.println("  export <wal_file> <output_file>");
        System.out.println("    将WAL数据导出为JSON格式");
        System.out.println();
        System.out.println("  interactive");
        System.out.println("    进入交互模式");
        System.out.println();
        System.out.println("  help");
        System.out.println("    显示此帮助信息");
        System.out.println();
        System.out.println("示例:");
        System.out.println("  # 分析WAL文件");
        System.out.println("  java WALAnalyzerCLI analyze /path/to/wal.log");
        System.out.println();
        System.out.println("  # 分析WAL文件并显示所有条目");
        System.out.println("  java WALAnalyzerCLI analyze /path/to/wal.log --show-entries");
        System.out.println();
        System.out.println("  # 验证WAL文件");
        System.out.println("  java WALAnalyzerCLI validate /path/to/wal.log");
        System.out.println();
        System.out.println("  # 导出WAL数据");
        System.out.println("  java WALAnalyzerCLI export /path/to/wal.log /tmp/wal_data.json");
        System.out.println();
        System.out.println("  # 交互模式");
        System.out.println("  java WALAnalyzerCLI interactive");
    }

    /**
     * 打印交互模式帮助
     */
    private static void printInteractiveHelp() {
        System.out.println("交互模式可用命令:");
        System.out.println("  analyze <wal_file> [--show-entries] - 分析WAL文件");
        System.out.println("  validate <wal_file>                 - 验证WAL文件");
        System.out.println("  export <wal_file> <output_file>     - 导出WAL数据");
        System.out.println("  help                                - 显示此帮助");
        System.out.println("  exit/quit                           - 退出交互模式");
    }

    /**
     * Java 8兼容的字符串重复方法
     */
    private static String repeatString(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
}