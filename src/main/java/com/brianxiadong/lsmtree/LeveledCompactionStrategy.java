package com.brianxiadong.lsmtree;

import java.io.IOException;
import java.util.*;

public class LeveledCompactionStrategy implements CompactionStrategy {
    private final String dataDir;
    private final int maxLevelSize;
    private final int levelSizeMultiplier;
    private CompressionStrategy compressionStrategy = new NoneCompressionStrategy();

    public LeveledCompactionStrategy(String dataDir, int maxLevelSize, int levelSizeMultiplier) {
        this.dataDir = dataDir;
        this.maxLevelSize = maxLevelSize;
        this.levelSizeMultiplier = levelSizeMultiplier;
    }

    /**
     * 检查是否需要压缩
     */
    public boolean needsCompaction(List<SSTable> ssTables) {
        Map<Integer, List<SSTable>> levelMap = groupByLevel(ssTables);

        for (Map.Entry<Integer, List<SSTable>> entry : levelMap.entrySet()) {
            int level = entry.getKey();
            List<SSTable> tablesInLevel = entry.getValue();

            int maxSize = level == 0 ? maxLevelSize : maxLevelSize * (int) Math.pow(levelSizeMultiplier, level);
            if (tablesInLevel.size() > maxSize) {
                return true;
            }
        }

        return false;
    }

    /**
     * 执行压缩操作
     */
    public List<SSTable> compact(List<SSTable> ssTables) throws IOException {
        Map<Integer, List<SSTable>> levelMap = groupByLevel(ssTables);
        List<SSTable> newTables = new ArrayList<>();

        for (Map.Entry<Integer, List<SSTable>> entry : levelMap.entrySet()) {
            int level = entry.getKey();
            List<SSTable> tablesInLevel = entry.getValue();

            int maxSize = level == 0 ? maxLevelSize : maxLevelSize * (int) Math.pow(levelSizeMultiplier, level);

            if (tablesInLevel.size() > maxSize) {
                // 需要压缩这个级别
                List<SSTable> compactedTables = compactLevel(tablesInLevel, level + 1);
                newTables.addAll(compactedTables);

                // 删除旧的SSTable文件
                for (SSTable oldTable : tablesInLevel) {
                    oldTable.delete();
                }
            } else {
                newTables.addAll(tablesInLevel);
            }
        }

        return newTables;
    }

    /**
     * 压缩指定级别的SSTable
     */
    private List<SSTable> compactLevel(List<SSTable> tables, int targetLevel) throws IOException {
        // 收集所有键值对
        List<KeyValue> allEntries = new ArrayList<>();

        for (SSTable table : tables) {
            allEntries.addAll(table.getAllEntries());
        }

        // 合并排序并去重
        List<KeyValue> mergedEntries = mergeAndDedup(allEntries);

        // 分割成多个SSTable（如果数据太大）
        List<SSTable> newTables = new ArrayList<>();
        int entriesPerTable = 10000; // 每个SSTable的最大条目数

        for (int i = 0; i < mergedEntries.size(); i += entriesPerTable) {
            int endIndex = Math.min(i + entriesPerTable, mergedEntries.size());
            List<KeyValue> tableEntries = mergedEntries.subList(i, endIndex);

            String fileName = String.format("%s/sstable_level%d_%d_%d.db",
                    dataDir, targetLevel, System.currentTimeMillis(), i);
            SSTable newTable = new SSTable(fileName, tableEntries, compressionStrategy);
            newTables.add(newTable);
        }

        return newTables;
    }

    /**
     * 合并和去重键值对
     * 保留每个键的最新版本
     */
    private List<KeyValue> mergeAndDedup(List<KeyValue> entries) {
        // 按键和时间戳排序
        entries.sort(KeyValue::compareTo);

        List<KeyValue> dedupedEntries = new ArrayList<>();
        Map<String, KeyValue> latestEntries = new HashMap<>();

        // 保留每个键的最新版本
        for (KeyValue entry : entries) {
            String key = entry.getKey();
            if (!latestEntries.containsKey(key) ||
                    entry.getTimestamp() > latestEntries.get(key).getTimestamp()) {
                latestEntries.put(key, entry);
            }
        }

        // 保留最新版本（包含墓碑）
        dedupedEntries.addAll(latestEntries.values());

        // 最终排序
        dedupedEntries.sort((a, b) -> a.getKey().compareTo(b.getKey()));

        return dedupedEntries;
    }

    /**
     * 按级别分组SSTable
     */
    private Map<Integer, List<SSTable>> groupByLevel(List<SSTable> ssTables) {
        Map<Integer, List<SSTable>> levelMap = new HashMap<>();

        for (SSTable table : ssTables) {
            int level = extractLevelFromPath(table.getFilePath());
            levelMap.computeIfAbsent(level, k -> new ArrayList<>()).add(table);
        }

        return levelMap;
    }

    /**
     * 从文件路径中提取级别信息
     */
    private int extractLevelFromPath(String filePath) {
        // 从文件名中解析级别，例如: sstable_level1_timestamp_index.db
        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
        if (fileName.contains("level")) {
            try {
                String levelStr = fileName.substring(fileName.indexOf("level") + 5);
                levelStr = levelStr.substring(0, levelStr.indexOf('_'));
                return Integer.parseInt(levelStr);
            } catch (Exception e) {
                return 0; // 默认级别
            }
        }
        return 0;
    }

    /**
     * 大小分层压缩策略
     * 当某个级别的SSTable数量超过阈值时触发压缩
     */
    public CompactionTask selectCompactionTask(List<SSTable> ssTables) {
        Map<Integer, List<SSTable>> levelMap = groupByLevel(ssTables);

        for (int level = 0; level < 10; level++) { // 最多10个级别
            List<SSTable> tablesInLevel = levelMap.getOrDefault(level, new ArrayList<>());
            int maxSize = level == 0 ? maxLevelSize : maxLevelSize * (int) Math.pow(levelSizeMultiplier, level);

            if (tablesInLevel.size() > maxSize) {
                return new CompactionTask(level, tablesInLevel);
            }
        }

        return null; // 不需要压缩
    }

    /**
     * 压缩任务类
     */
    public static class CompactionTask {
        private final int level;
        private final List<SSTable> tables;

        public CompactionTask(int level, List<SSTable> tables) {
            this.level = level;
            this.tables = tables;
        }

        public int getLevel() {
            return level;
        }

        public List<SSTable> getTables() {
            return tables;
        }
    }

    @Override
    public void setCompressionStrategy(CompressionStrategy compressionStrategy) {
        this.compressionStrategy = compressionStrategy == null ? new NoneCompressionStrategy() : compressionStrategy;
    }
}
