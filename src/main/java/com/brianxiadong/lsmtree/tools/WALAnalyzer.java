package com.brianxiadong.lsmtree.tools;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

import com.brianxiadong.lsmtree.WriteAheadLog;

/**
 * WAL (Write-Ahead Log) 文件分析器
 * 提供WAL文件的解析、统计、验证和分析功能
 */
public class WALAnalyzer {

    /**
     * WAL分析结果
     */
    public static class WALAnalysisResult {
        private final String filePath;
        private final long fileSize;
        private final long lastModified;
        private final List<WriteAheadLog.LogEntry> entries;
        private final WALStatistics statistics;
        private final List<String> errors;

        public WALAnalysisResult(String filePath, long fileSize, long lastModified,
                List<WriteAheadLog.LogEntry> entries, WALStatistics statistics,
                List<String> errors) {
            this.filePath = filePath;
            this.fileSize = fileSize;
            this.lastModified = lastModified;
            this.entries = entries;
            this.statistics = statistics;
            this.errors = errors;
        }

        // Getters
        public String getFilePath() {
            return filePath;
        }

        public long getFileSize() {
            return fileSize;
        }

        public long getLastModified() {
            return lastModified;
        }

        public List<WriteAheadLog.LogEntry> getEntries() {
            return entries;
        }

        public WALStatistics getStatistics() {
            return statistics;
        }

        public List<String> getErrors() {
            return errors;
        }
    }

    /**
     * WAL统计信息
     */
    public static class WALStatistics {
        private final int totalEntries;
        private final int putOperations;
        private final int deleteOperations;
        private final int invalidEntries;
        private final long timeSpan;
        private final long firstTimestamp;
        private final long lastTimestamp;
        private final Set<String> uniqueKeys;
        private final Map<String, Integer> keyFrequency;

        public WALStatistics(int totalEntries, int putOperations, int deleteOperations,
                int invalidEntries, long timeSpan, long firstTimestamp,
                long lastTimestamp, Set<String> uniqueKeys,
                Map<String, Integer> keyFrequency) {
            this.totalEntries = totalEntries;
            this.putOperations = putOperations;
            this.deleteOperations = deleteOperations;
            this.invalidEntries = invalidEntries;
            this.timeSpan = timeSpan;
            this.firstTimestamp = firstTimestamp;
            this.lastTimestamp = lastTimestamp;
            this.uniqueKeys = uniqueKeys;
            this.keyFrequency = keyFrequency;
        }

        // Getters
        public int getTotalEntries() {
            return totalEntries;
        }

        public int getPutOperations() {
            return putOperations;
        }

        public int getDeleteOperations() {
            return deleteOperations;
        }

        public int getInvalidEntries() {
            return invalidEntries;
        }

        public long getTimeSpan() {
            return timeSpan;
        }

        public long getFirstTimestamp() {
            return firstTimestamp;
        }

        public long getLastTimestamp() {
            return lastTimestamp;
        }

        public Set<String> getUniqueKeys() {
            return uniqueKeys;
        }

        public Map<String, Integer> getKeyFrequency() {
            return keyFrequency;
        }

        public double getDeleteRatio() {
            return totalEntries > 0 ? (double) deleteOperations / totalEntries : 0.0;
        }
    }

    /**
     * 分析WAL文件
     */
    public static WALAnalysisResult analyzeWAL(String filePath) throws IOException {
        Path path = Paths.get(filePath);

        if (!Files.exists(path)) {
            throw new FileNotFoundException("WAL文件不存在: " + filePath);
        }

        long fileSize = Files.size(path);
        long lastModified = Files.getLastModifiedTime(path).toMillis();

        List<WriteAheadLog.LogEntry> entries = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // 解析WAL文件
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (line.trim().isEmpty()) {
                    continue; // 跳过空行
                }

                WriteAheadLog.LogEntry entry = WriteAheadLog.LogEntry.fromString(line);
                if (entry != null) {
                    entries.add(entry);
                } else {
                    errors.add("第" + lineNumber + "行: 无效的日志条目格式 - " + line);
                }
            }
        }

        // 计算统计信息
        WALStatistics statistics = calculateStatistics(entries);

        return new WALAnalysisResult(filePath, fileSize, lastModified, entries, statistics, errors);
    }

    /**
     * 计算WAL统计信息
     */
    private static WALStatistics calculateStatistics(List<WriteAheadLog.LogEntry> entries) {
        int totalEntries = entries.size();
        int putOperations = 0;
        int deleteOperations = 0;
        long firstTimestamp = Long.MAX_VALUE;
        long lastTimestamp = Long.MIN_VALUE;
        Set<String> uniqueKeys = new HashSet<>();
        Map<String, Integer> keyFrequency = new HashMap<>();

        for (WriteAheadLog.LogEntry entry : entries) {
            // 统计操作类型
            if (entry.getOperation() == WriteAheadLog.Operation.PUT) {
                putOperations++;
            } else if (entry.getOperation() == WriteAheadLog.Operation.DELETE) {
                deleteOperations++;
            }

            // 统计时间范围
            long timestamp = entry.getTimestamp();
            if (timestamp < firstTimestamp) {
                firstTimestamp = timestamp;
            }
            if (timestamp > lastTimestamp) {
                lastTimestamp = timestamp;
            }

            // 统计键
            String key = entry.getKey();
            uniqueKeys.add(key);
            keyFrequency.put(key, keyFrequency.getOrDefault(key, 0) + 1);
        }

        long timeSpan = totalEntries > 0 ? lastTimestamp - firstTimestamp : 0;
        if (totalEntries == 0) {
            firstTimestamp = 0;
            lastTimestamp = 0;
        }

        return new WALStatistics(totalEntries, putOperations, deleteOperations, 0,
                timeSpan, firstTimestamp, lastTimestamp, uniqueKeys, keyFrequency);
    }

    /**
     * 验证WAL文件完整性
     */
    public static boolean validateWAL(String filePath) {
        try {
            WALAnalysisResult result = analyzeWAL(filePath);
            return result.getErrors().isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 格式化输出WAL分析结果
     */
    public static String formatAnalysisResult(WALAnalysisResult result, boolean showEntries) {
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        // 文件信息
        sb.append(repeatString("=", 60)).append("\n");
        sb.append("WAL文件分析报告\n");
        sb.append(repeatString("=", 60)).append("\n");
        sb.append("文件路径: ").append(result.getFilePath()).append("\n");
        sb.append("文件大小: ").append(formatFileSize(result.getFileSize())).append("\n");
        sb.append("最后修改: ").append(dateFormat.format(new Date(result.getLastModified()))).append("\n");
        sb.append("文件状态: ").append(result.getErrors().isEmpty() ? "正常" : "有错误").append("\n");
        sb.append("\n");

        // 统计信息
        WALStatistics stats = result.getStatistics();
        sb.append(repeatString("-", 40)).append("\n");
        sb.append("统计信息\n");
        sb.append(repeatString("-", 40)).append("\n");
        sb.append("总条目数: ").append(stats.getTotalEntries()).append("\n");
        sb.append("PUT操作: ").append(stats.getPutOperations()).append("\n");
        sb.append("DELETE操作: ").append(stats.getDeleteOperations()).append("\n");
        sb.append("删除比例: ").append(String.format("%.2f%%", stats.getDeleteRatio() * 100)).append("\n");
        sb.append("唯一键数: ").append(stats.getUniqueKeys().size()).append("\n");

        if (stats.getTotalEntries() > 0) {
            sb.append("时间跨度: ").append(formatDuration(stats.getTimeSpan())).append("\n");
            sb.append("开始时间: ").append(dateFormat.format(new Date(stats.getFirstTimestamp()))).append("\n");
            sb.append("结束时间: ").append(dateFormat.format(new Date(stats.getLastTimestamp()))).append("\n");
        }
        sb.append("\n");

        // 错误信息
        if (!result.getErrors().isEmpty()) {
            sb.append(repeatString("-", 40)).append("\n");
            sb.append("错误信息\n");
            sb.append(repeatString("-", 40)).append("\n");
            for (String error : result.getErrors()) {
                sb.append("❌ ").append(error).append("\n");
            }
            sb.append("\n");
        }

        // 条目详情
        if (showEntries && !result.getEntries().isEmpty()) {
            sb.append(repeatString("-", 40)).append("\n");
            sb.append("日志条目\n");
            sb.append(repeatString("-", 40)).append("\n");
            sb.append(String.format("%-10s %-20s %-30s %-20s\n", "操作", "键", "值", "时间戳"));
            sb.append(repeatString("-", 80)).append("\n");

            for (WriteAheadLog.LogEntry entry : result.getEntries()) {
                String value = entry.getValue();
                if (value != null && value.length() > 25) {
                    value = value.substring(0, 25) + "...";
                }
                sb.append(String.format("%-10s %-20s %-30s %-20s\n",
                        entry.getOperation(),
                        entry.getKey(),
                        value != null ? value : "",
                        dateFormat.format(new Date(entry.getTimestamp()))));
            }
        }

        return sb.toString();
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

    /**
     * 转义JSON字符串中的特殊字符
     */
    private static String escapeJson(String str) {
        if (str == null) {
            return null;
        }
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 格式化文件大小
     */
    private static String formatFileSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * 格式化时间跨度
     */
    private static String formatDuration(long milliseconds) {
        if (milliseconds < 1000)
            return milliseconds + " ms";
        if (milliseconds < 60000)
            return String.format("%.1f s", milliseconds / 1000.0);
        if (milliseconds < 3600000)
            return String.format("%.1f min", milliseconds / 60000.0);
        return String.format("%.1f h", milliseconds / 3600000.0);
    }

    /**
     * 导出WAL数据为JSON格式
     */
    public static void exportToJSON(WALAnalysisResult result, String outputPath) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
            writer.println("{");
            writer.println("  \"file_info\": {");
            writer.println("    \"path\": \"" + result.getFilePath() + "\",");
            writer.println("    \"size\": " + result.getFileSize() + ",");
            writer.println("    \"last_modified\": " + result.getLastModified());
            writer.println("  },");

            WALStatistics stats = result.getStatistics();
            writer.println("  \"statistics\": {");
            writer.println("    \"total_entries\": " + stats.getTotalEntries() + ",");
            writer.println("    \"put_operations\": " + stats.getPutOperations() + ",");
            writer.println("    \"delete_operations\": " + stats.getDeleteOperations() + ",");
            writer.println("    \"unique_keys\": " + stats.getUniqueKeys().size() + ",");
            writer.println("    \"time_span\": " + stats.getTimeSpan() + ",");
            writer.println("    \"first_timestamp\": " + stats.getFirstTimestamp() + ",");
            writer.println("    \"last_timestamp\": " + stats.getLastTimestamp());
            writer.println("  },");

            writer.println("  \"entries\": [");
            List<WriteAheadLog.LogEntry> entries = result.getEntries();
            for (int i = 0; i < entries.size(); i++) {
                WriteAheadLog.LogEntry entry = entries.get(i);
                writer.println("    {");
                writer.println("      \"operation\": \"" + entry.getOperation() + "\",");
                writer.println("      \"key\": \"" + entry.getKey() + "\",");
                writer.println("      \"value\": "
                        + (entry.getValue() != null ? "\"" + escapeJson(entry.getValue()) + "\"" : "null") + ",");
                writer.println("      \"timestamp\": " + entry.getTimestamp());
                writer.print("    }");
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
}