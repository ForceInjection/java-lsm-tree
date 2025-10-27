package com.brianxiadong.lsmtree.tools;

import com.brianxiadong.lsmtree.KeyValue;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * SSTable 文件分析工具
 * 用于分析和调试 LSM Tree 的磁盘文件
 */
public class SSTableAnalyzer {
    
    /**
     * 文件分析结果
     */
    public static class AnalysisResult {
        private final String filePath;
        private final long fileSize;
        private final int entryCount;
        private final List<KeyValue> entries;
        private final long creationTime;
        private final boolean isValid;
        private final String errorMessage;
        
        // 统计信息
        private final int deletedCount;
        private final int activeCount;
        private final long totalKeySize;
        private final long totalValueSize;
        private final double compressionRatio;
        
        public AnalysisResult(String filePath, long fileSize, int entryCount, 
                            List<KeyValue> entries, long creationTime, boolean isValid, 
                            String errorMessage, int deletedCount, int activeCount,
                            long totalKeySize, long totalValueSize, double compressionRatio) {
            this.filePath = filePath;
            this.fileSize = fileSize;
            this.entryCount = entryCount;
            this.entries = entries;
            this.creationTime = creationTime;
            this.isValid = isValid;
            this.errorMessage = errorMessage;
            this.deletedCount = deletedCount;
            this.activeCount = activeCount;
            this.totalKeySize = totalKeySize;
            this.totalValueSize = totalValueSize;
            this.compressionRatio = compressionRatio;
        }
        
        // Getters
        public String getFilePath() { return filePath; }
        public long getFileSize() { return fileSize; }
        public int getEntryCount() { return entryCount; }
        public List<KeyValue> getEntries() { return entries; }
        public long getCreationTime() { return creationTime; }
        public boolean isValid() { return isValid; }
        public String getErrorMessage() { return errorMessage; }
        public int getDeletedCount() { return deletedCount; }
        public int getActiveCount() { return activeCount; }
        public long getTotalKeySize() { return totalKeySize; }
        public long getTotalValueSize() { return totalValueSize; }
        public double getCompressionRatio() { return compressionRatio; }
    }
    
    /**
     * 分析SSTable文件
     */
    public static AnalysisResult analyzeFile(String filePath) {
        Path path = Paths.get(filePath);
        
        if (!Files.exists(path)) {
            return new AnalysisResult(filePath, 0, 0, Collections.emptyList(), 
                                    0, false, "文件不存在", 0, 0, 0, 0, 0.0);
        }
        
        try {
            long fileSize = Files.size(path);
            long creationTime = Files.getLastModifiedTime(path).toMillis();
            
            List<KeyValue> entries = new ArrayList<>();
            int entryCount = 0;
            int deletedCount = 0;
            int activeCount = 0;
            long totalKeySize = 0;
            long totalValueSize = 0;
            
            try (DataInputStream dis = new DataInputStream(
                    new BufferedInputStream(new FileInputStream(filePath)))) {
                
                entryCount = dis.readInt();
                
                for (int i = 0; i < entryCount; i++) {
                    String key = dis.readUTF();
                    boolean deleted = dis.readBoolean();
                    String value = null;
                    if (!deleted) {
                        value = dis.readUTF();
                        totalValueSize += value.getBytes("UTF-8").length;
                        activeCount++;
                    } else {
                        deletedCount++;
                    }
                    long timestamp = dis.readLong();
                    
                    totalKeySize += key.getBytes("UTF-8").length;
                    entries.add(new KeyValue(key, value, timestamp, deleted));
                }
            }
            
            // 验证数据有序性
            boolean isOrdered = isDataOrdered(entries);
            if (!isOrdered) {
                return new AnalysisResult(filePath, fileSize, entryCount, entries, 
                                        creationTime, false, "数据未按键排序", 
                                        deletedCount, activeCount, totalKeySize, totalValueSize, 0.0);
            }
            
            // 计算压缩比（估算）
            long rawDataSize = totalKeySize + totalValueSize + (entryCount * 16); // 16字节用于timestamp和deleted标记
            double compressionRatio = rawDataSize > 0 ? (double) fileSize / rawDataSize : 1.0;
            
            return new AnalysisResult(filePath, fileSize, entryCount, entries, 
                                    creationTime, true, null, deletedCount, activeCount,
                                    totalKeySize, totalValueSize, compressionRatio);
            
        } catch (IOException e) {
            return new AnalysisResult(filePath, 0, 0, Collections.emptyList(), 
                                    0, false, "读取文件失败: " + e.getMessage(), 
                                    0, 0, 0, 0, 0.0);
        }
    }
    
    /**
     * 验证数据是否按键排序
     */
    private static boolean isDataOrdered(List<KeyValue> entries) {
        for (int i = 1; i < entries.size(); i++) {
            if (entries.get(i-1).getKey().compareTo(entries.get(i).getKey()) > 0) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 打印分析结果
     */
    public static void printAnalysisResult(AnalysisResult result) {
        System.out.println("=== SSTable 文件分析报告 ===");
        System.out.println("文件路径: " + result.getFilePath());
        System.out.println("文件大小: " + formatFileSize(result.getFileSize()));
        System.out.println("创建时间: " + formatTimestamp(result.getCreationTime()));
        System.out.println("文件状态: " + (result.isValid() ? "有效" : "无效"));
        
        if (!result.isValid()) {
            System.out.println("错误信息: " + result.getErrorMessage());
            return;
        }
        
        System.out.println();
        System.out.println("=== 数据统计 ===");
        System.out.println("总条目数: " + result.getEntryCount());
        System.out.println("活跃条目: " + result.getActiveCount());
        System.out.println("删除条目: " + result.getDeletedCount());
        System.out.println("删除率: " + String.format("%.2f%%", 
                          (double) result.getDeletedCount() / result.getEntryCount() * 100));
        
        System.out.println();
        System.out.println("=== 存储统计 ===");
        System.out.println("键总大小: " + formatFileSize(result.getTotalKeySize()));
        System.out.println("值总大小: " + formatFileSize(result.getTotalValueSize()));
        System.out.println("平均键大小: " + String.format("%.1f bytes", 
                          (double) result.getTotalKeySize() / result.getEntryCount()));
        if (result.getActiveCount() > 0) {
            System.out.println("平均值大小: " + String.format("%.1f bytes", 
                              (double) result.getTotalValueSize() / result.getActiveCount()));
        }
        System.out.println("存储效率: " + String.format("%.2f", result.getCompressionRatio()));
    }
    
    /**
     * 打印数据内容（可选择范围）
     */
    public static void printDataContent(AnalysisResult result, int maxEntries) {
        if (!result.isValid()) {
            System.out.println("文件无效，无法显示内容");
            return;
        }
        
        System.out.println();
        System.out.println("=== 数据内容 ===");
        List<KeyValue> entries = result.getEntries();
        int displayCount = Math.min(maxEntries, entries.size());
        
        System.out.printf("%-20s %-10s %-30s %-20s%n", "键", "状态", "值", "时间戳");
        System.out.println("--------------------------------------------------------------------------------");
        
        for (int i = 0; i < displayCount; i++) {
            KeyValue kv = entries.get(i);
            String status = kv.isDeleted() ? "已删除" : "活跃";
            String value = kv.isDeleted() ? "-" : 
                          (kv.getValue().length() > 25 ? kv.getValue().substring(0, 25) + "..." : kv.getValue());
            String timestamp = formatTimestamp(kv.getTimestamp());
            
            System.out.printf("%-20s %-10s %-30s %-20s%n", 
                            kv.getKey(), status, value, timestamp);
        }
        
        if (entries.size() > maxEntries) {
            System.out.println("... 还有 " + (entries.size() - maxEntries) + " 条记录");
        }
    }
    
    /**
     * 导出数据为JSON格式
     */
    public static void exportToJson(AnalysisResult result, String outputPath) throws IOException {
        if (!result.isValid()) {
            throw new IOException("文件无效，无法导出");
        }
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
            writer.println("{");
            writer.println("  \"file_info\": {");
            writer.println("    \"path\": \"" + result.getFilePath() + "\",");
            writer.println("    \"size\": " + result.getFileSize() + ",");
            writer.println("    \"creation_time\": " + result.getCreationTime());
            writer.println("  },");
            writer.println("  \"statistics\": {");
            writer.println("    \"total_entries\": " + result.getEntryCount() + ",");
            writer.println("    \"active_entries\": " + result.getActiveCount() + ",");
            writer.println("    \"deleted_entries\": " + result.getDeletedCount() + ",");
            writer.println("    \"total_key_size\": " + result.getTotalKeySize() + ",");
            writer.println("    \"total_value_size\": " + result.getTotalValueSize() + ",");
            writer.println("    \"compression_ratio\": " + result.getCompressionRatio());
            writer.println("  },");
            writer.println("  \"entries\": [");
            
            List<KeyValue> entries = result.getEntries();
            for (int i = 0; i < entries.size(); i++) {
                KeyValue kv = entries.get(i);
                writer.print("    {");
                writer.print("\"key\": \"" + escapeJson(kv.getKey()) + "\", ");
                writer.print("\"value\": " + (kv.isDeleted() ? "null" : "\"" + escapeJson(kv.getValue()) + "\"") + ", ");
                writer.print("\"timestamp\": " + kv.getTimestamp() + ", ");
                writer.print("\"deleted\": " + kv.isDeleted());
                writer.print("}");
                if (i < entries.size() - 1) {
                    writer.print(",");
                }
                writer.println();
            }
            
            writer.println("  ]");
            writer.println("}");
        }
    }
    
    /**
     * 比较两个SSTable文件
     */
    public static void compareFiles(String file1Path, String file2Path) {
        AnalysisResult result1 = analyzeFile(file1Path);
        AnalysisResult result2 = analyzeFile(file2Path);
        
        System.out.println("=== SSTable 文件对比 ===");
        System.out.printf("%-20s %-30s %-30s%n", "属性", "文件1", "文件2");
        System.out.println("--------------------------------------------------------------------------------");
        
        System.out.printf("%-20s %-30s %-30s%n", "文件路径", 
                         truncate(result1.getFilePath(), 28), truncate(result2.getFilePath(), 28));
        System.out.printf("%-20s %-30s %-30s%n", "文件大小", 
                         formatFileSize(result1.getFileSize()), formatFileSize(result2.getFileSize()));
        System.out.printf("%-20s %-30d %-30d%n", "条目数量", 
                         result1.getEntryCount(), result2.getEntryCount());
        System.out.printf("%-20s %-30d %-30d%n", "活跃条目", 
                         result1.getActiveCount(), result2.getActiveCount());
        System.out.printf("%-20s %-30d %-30d%n", "删除条目", 
                         result1.getDeletedCount(), result2.getDeletedCount());
        System.out.printf("%-20s %-30s %-30s%n", "存储效率", 
                         String.format("%.2f", result1.getCompressionRatio()),
                         String.format("%.2f", result2.getCompressionRatio()));
    }
    
    // 工具方法
    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    private static String formatTimestamp(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp));
    }
    
    private static String escapeJson(String str) {
        return str.replace("\\", "\\\\").replace("\"", "\\\"");
    }
    
    private static String truncate(String str, int maxLength) {
        return str.length() > maxLength ? str.substring(0, maxLength - 3) + "..." : str;
    }
}