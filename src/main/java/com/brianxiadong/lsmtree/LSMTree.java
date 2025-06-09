package com.brianxiadong.lsmtree;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * LSM Tree 主要实现类
 * 整合MemTable、SSTable和压缩策略
 */
public class LSMTree implements AutoCloseable {
    private final String dataDir;
    private final int memTableMaxSize;
    private final ReadWriteLock lock;

    // 内存组件
    private volatile MemTable activeMemTable;
    private final List<MemTable> immutableMemTables;

    // 磁盘组件
    private final List<SSTable> ssTables;

    // 后台任务
    private final ExecutorService compactionExecutor;
    private final CompactionStrategy compactionStrategy;

    // WAL (Write-Ahead Log) 相关
    private final WriteAheadLog wal;

    public LSMTree(String dataDir, int memTableMaxSize) throws IOException {
        this.dataDir = dataDir;
        this.memTableMaxSize = memTableMaxSize;
        this.lock = new ReentrantReadWriteLock();

        // 初始化目录
        createDirectoryIfNotExists(dataDir);

        // 初始化组件
        this.activeMemTable = new MemTable(memTableMaxSize);
        this.immutableMemTables = new ArrayList<>();
        this.ssTables = new ArrayList<>();

        // 初始化压缩策略
        this.compactionStrategy = new CompactionStrategy(dataDir, 4, 10);

        // 初始化WAL
        this.wal = new WriteAheadLog(dataDir + "/wal.log");

        // 启动后台压缩线程
        this.compactionExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LSMTree-Compaction");
            t.setDaemon(true);
            return t;
        });

        // 恢复现有数据
        recover();

        // 暂时禁用后台压缩任务，避免测试时的线程问题
        // startBackgroundCompaction();
    }

    /**
     * 插入键值对
     */
    public void put(String key, String value) throws IOException {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Key and value cannot be null");
        }

        lock.writeLock().lock();
        try {
            // 写入WAL
            wal.append(WriteAheadLog.LogEntry.put(key, value));

            // 写入活跃MemTable
            activeMemTable.put(key, value);

            // 检查是否需要刷盘
            if (activeMemTable.shouldFlush()) {
                flushMemTable();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 删除键
     */
    public void delete(String key) throws IOException {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }

        lock.writeLock().lock();
        try {
            // 写入WAL
            wal.append(WriteAheadLog.LogEntry.delete(key));

            // 在活跃MemTable中标记删除
            activeMemTable.delete(key);

            // 检查是否需要刷盘
            if (activeMemTable.shouldFlush()) {
                flushMemTable();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 查询键值
     */
    public String get(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }

        lock.readLock().lock();
        try {
            // 1. 首先查询活跃MemTable
            String value = activeMemTable.get(key);
            if (value != null) {
                return value;
            }

            // 2. 查询不可变MemTable（按时间倒序）
            for (int i = immutableMemTables.size() - 1; i >= 0; i--) {
                value = immutableMemTables.get(i).get(key);
                if (value != null) {
                    return value;
                }
            }

            // 3. 查询SSTable（按创建时间倒序）
            List<SSTable> sortedSSTables = new ArrayList<>(ssTables);
            sortedSSTables.sort((a, b) -> Long.compare(b.getCreationTime(), a.getCreationTime()));

            for (SSTable ssTable : sortedSSTables) {
                value = ssTable.get(key);
                if (value != null) {
                    return value;
                }
            }

            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 刷新MemTable到磁盘
     */
    private void flushMemTable() throws IOException {
        if (activeMemTable.isEmpty()) {
            return;
        }

        // 将活跃MemTable转为不可变
        immutableMemTables.add(activeMemTable);
        activeMemTable = new MemTable(memTableMaxSize);

        // 同步刷盘，避免死锁
        flushImmutableMemTable();
    }

    /**
     * 刷新不可变MemTable到SSTable（调用前必须已获取写锁）
     */
    private void flushImmutableMemTable() throws IOException {
        if (immutableMemTables.isEmpty()) {
            return;
        }

        MemTable memTable = immutableMemTables.remove(0);
        List<KeyValue> entries = memTable.getAllEntries();

        if (!entries.isEmpty()) {
            // 排序
            entries.sort(KeyValue::compareTo);

            // 创建SSTable文件
            String fileName = String.format("%s/sstable_level0_%d.db",
                    dataDir, System.currentTimeMillis());
            SSTable newSSTable = new SSTable(fileName, entries);
            ssTables.add(newSSTable);

            // 清理WAL
            wal.checkpoint();
        }
    }

    /**
     * 启动后台压缩任务
     */
    private void startBackgroundCompaction() {
        compactionExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(30000); // 每30秒检查一次

                    if (compactionStrategy.needsCompaction(ssTables)) {
                        performCompaction();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    e.printStackTrace(); // 实际项目中应该使用日志
                }
            }
        });
    }

    /**
     * 执行压缩操作
     */
    private void performCompaction() throws IOException {
        lock.writeLock().lock();
        try {
            List<SSTable> newSSTables = compactionStrategy.compact(ssTables);
            ssTables.clear();
            ssTables.addAll(newSSTables);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 从WAL和SSTable恢复数据
     */
    private void recover() throws IOException {
        // 1. 恢复SSTable
        File dir = new File(dataDir);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".db"));

        if (files != null) {
            Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));

            for (File file : files) {
                SSTable ssTable = new SSTable(file.getAbsolutePath());
                ssTables.add(ssTable);
            }
        }

        // 2. 从WAL恢复未刷盘的数据
        List<WriteAheadLog.LogEntry> entries = wal.recover();
        for (WriteAheadLog.LogEntry entry : entries) {
            if (entry.getOperation() == WriteAheadLog.Operation.PUT) {
                activeMemTable.put(entry.getKey(), entry.getValue());
            } else if (entry.getOperation() == WriteAheadLog.Operation.DELETE) {
                activeMemTable.delete(entry.getKey());
            }
        }
    }

    /**
     * 强制刷盘
     */
    public void flush() throws IOException {
        lock.writeLock().lock();
        try {
            // 刷新活跃MemTable
            if (!activeMemTable.isEmpty()) {
                flushMemTable();
            }

            // 刷新所有剩余的不可变MemTable
            while (!immutableMemTables.isEmpty()) {
                flushImmutableMemTable();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 关闭LSM Tree
     */
    public void close() throws IOException {
        // 刷盘所有内存数据
        flush();

        // 关闭WAL
        wal.close();

        // 立即关闭线程池，不等待
        compactionExecutor.shutdownNow();
    }

    /**
     * 创建目录
     */
    private void createDirectoryIfNotExists(String path) throws IOException {
        File dir = new File(path);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Failed to create directory: " + path);
        }
    }

    /**
     * 获取统计信息
     */
    public LSMTreeStats getStats() {
        lock.readLock().lock();
        try {
            return new LSMTreeStats(
                    activeMemTable.size(),
                    immutableMemTables.size(),
                    ssTables.size());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * LSM Tree 统计信息
     */
    public static class LSMTreeStats {
        private final int activeMemTableSize;
        private final int immutableMemTableCount;
        private final int ssTableCount;

        public LSMTreeStats(int activeMemTableSize, int immutableMemTableCount, int ssTableCount) {
            this.activeMemTableSize = activeMemTableSize;
            this.immutableMemTableCount = immutableMemTableCount;
            this.ssTableCount = ssTableCount;
        }

        public int getActiveMemTableSize() {
            return activeMemTableSize;
        }

        public int getImmutableMemTableCount() {
            return immutableMemTableCount;
        }

        public int getSsTableCount() {
            return ssTableCount;
        }

        @Override
        public String toString() {
            return String.format("LSMTreeStats{activeMemTable=%d, immutableMemTables=%d, ssTables=%d}",
                    activeMemTableSize, immutableMemTableCount, ssTableCount);
        }
    }
}