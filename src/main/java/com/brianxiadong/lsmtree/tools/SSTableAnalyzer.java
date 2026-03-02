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
 * <p>
 * 提供对 SSTable 文件的底层分析能力，包括：
 * <ul>
 *   <li>解析文件头和数据条目</li>
 *   <li>验证数据完整性和有序性</li>
 *   <li>统计活跃/删除条目、压缩比等指标</li>
 * </ul>
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
     * 格式化文件大小
     */
    private static String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        return String.format("%.2f MB", size / (1024.0 * 1024.0));
    }
    
    /**
     * 格式化时间戳
     */
    private static String formatTimestamp(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp));
    }
    
    /**
     * 比较两个SSTable文件
     */
    public static void compareFiles(String file1, String file2) {
        System.out.println("比较文件:");
        System.out.println("1: " + file1);
        System.out.println("2: " + file2);
        
        AnalysisResult res1 = analyzeFile(file1);
        AnalysisResult res2 = analyzeFile(file2);
        
        if (!res1.isValid()) {
            System.out.println("文件1无效: " + res1.getErrorMessage());
            return;
        }
        
        if (!res2.isValid()) {
            System.out.println("文件2无效: " + res2.getErrorMessage());
            return;
        }
        
        // 比较基本信息
        System.out.println("\n=== 基本信息比较 ===");
        System.out.printf("%-15s %-20s %-20s%n", "指标", "文件1", "文件2");
        System.out.println("---------------------------------------------------------");
        System.out.printf("%-15s %-20s %-20s%n", "大小", formatFileSize(res1.getFileSize()), formatFileSize(res2.getFileSize()));
        System.out.printf("%-15s %-20d %-20d%n", "总条目", res1.getEntryCount(), res2.getEntryCount());
        System.out.printf("%-15s %-20d %-20d%n", "活跃条目", res1.getActiveCount(), res2.getActiveCount());
        System.out.printf("%-15s %-20d %-20d%n", "删除条目", res1.getDeletedCount(), res2.getDeletedCount());
        
        // 比较内容差异
        System.out.println("\n=== 内容差异 ===");
        Set<String> keys1 = new HashSet<>();
        res1.getEntries().forEach(kv -> keys1.add(kv.getKey()));
        
        Set<String> keys2 = new HashSet<>();
        res2.getEntries().forEach(kv -> keys2.add(kv.getKey()));
        
        Set<String> onlyIn1 = new HashSet<>(keys1);
        onlyIn1.removeAll(keys2);
        
        Set<String> onlyIn2 = new HashSet<>(keys2);
        onlyIn2.removeAll(keys1);
        
        Set<String> common = new HashSet<>(keys1);
        common.retainAll(keys2);
        
        System.out.println("仅在文件1中的键: " + onlyIn1.size());
        if (!onlyIn1.isEmpty() && onlyIn1.size() < 10) {
            System.out.println("  " + onlyIn1);
        }
        
        System.out.println("仅在文件2中的键: " + onlyIn2.size());
        if (!onlyIn2.isEmpty() && onlyIn2.size() < 10) {
            System.out.println("  " + onlyIn2);
        }
        
        // 比较共同键的值
        int diffCount = 0;
        for (String key : common) {
            KeyValue kv1 = findByKey(res1.getEntries(), key);
            KeyValue kv2 = findByKey(res2.getEntries(), key);
            
            if (!isEqual(kv1, kv2)) {
                diffCount++;
                if (diffCount <= 5) {
                    System.out.println("键 '" + key + "' 存在差异:");
                    System.out.println("  文件1: " + formatKV(kv1));
                    System.out.println("  文件2: " + formatKV(kv2));
                }
            }
        }
        
        System.out.println("值不一致的键数量: " + diffCount);
    }
    
    private static KeyValue findByKey(List<KeyValue> entries, String key) {
        for (KeyValue kv : entries) {
            if (kv.getKey().equals(key)) return kv;
        }
        return null;
    }
    
    private static boolean isEqual(KeyValue kv1, KeyValue kv2) {
        if (kv1.isDeleted() != kv2.isDeleted()) return false;
        if (kv1.isDeleted()) return true; // 都是删除状态，视为相同
        return Objects.equals(kv1.getValue(), kv2.getValue());
    }
    
    private static String formatKV(KeyValue kv) {
        if (kv.isDeleted()) return "[DELETED] ts=" + kv.getTimestamp();
        return "[VALUE='" + kv.getValue() + "'] ts=" + kv.getTimestamp();
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
            String value = kv.getValue() == null ? "-" : kv.getValue();
            if (value.length() > 28) value = value.substring(0, 25) + "...";
            String timestamp = formatTimestamp(kv.getTimestamp());
            
            System.out.printf("%-20s %-10s %-30s %-20s%n", 
                            kv.getKey(), status, value, timestamp);
        }
        
        if (entries.size() > displayCount) {
            System.out.println("... (还有 " + (entries.size() - displayCount) + " 条数据)");
        }
    }

    /**
     * 导出为 JSON
     */
    public static void exportToJson(AnalysisResult result, String outputFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            writer.println("{");
            writer.println("  \"file\": \"" + result.getFilePath() + "\",");
            writer.println("  \"size\": " + result.getFileSize() + ",");
            writer.println("  \"created\": " + result.getCreationTime() + ",");
            writer.println("  \"entries\": [");
            
            List<KeyValue> entries = result.getEntries();
            for (int i = 0; i < entries.size(); i++) {
                KeyValue kv = entries.get(i);
                writer.print("    {");
                writer.print("\"key\": \"" + escapeJson(kv.getKey()) + "\", ");
                writer.print("\"deleted\": " + kv.isDeleted() + ", ");
                if (!kv.isDeleted()) {
                    writer.print("\"value\": \"" + escapeJson(kv.getValue()) + "\", ");
                }
                writer.print("\"timestamp\": " + kv.getTimestamp());
                writer.print("}");
                if (i < entries.size() - 1) {
                    writer.println(",");
                } else {
                    writer.println();
                }
            }
            
            writer.println("  ]");
            writer.println("}");
        }
    }
    
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"").replace("\\", "\\\\");
    }
}