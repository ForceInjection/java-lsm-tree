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
    private final CompressionStrategy compressionStrategy;
    private final LSMTreeMetrics metrics;

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

        this.compactionStrategy = new LeveledCompactionStrategy(dataDir, 4, 10);
        this.compressionStrategy = new NoneCompressionStrategy();
        this.compactionStrategy.setCompressionStrategy(this.compressionStrategy);
        this.metrics = new MicrometerLSMTreeMetrics("default");

        // 初始化WAL
        this.wal = new WriteAheadLog(dataDir + "/wal.log");

        io.micrometer.core.instrument.MeterRegistry registry = MetricsRegistry.get();
        io.micrometer.core.instrument.Gauge.builder("lsm.memtable.size", this, t -> t.activeMemTable.size())
                .register(registry);
        io.micrometer.core.instrument.Gauge.builder("lsm.sstable.count", this, t -> t.ssTables.size())
                .register(registry);
        io.micrometer.core.instrument.Gauge.builder("lsm.level.count", this, t -> t.countLevel(0)).tag("level", "0")
                .register(registry);
        io.micrometer.core.instrument.Gauge.builder("lsm.level.count", this, t -> t.countLevel(1)).tag("level", "1")
                .register(registry);
        io.micrometer.core.instrument.Gauge.builder("lsm.level.count", this, t -> t.countLevel(2)).tag("level", "2")
                .register(registry);
        io.micrometer.core.instrument.Gauge.builder("lsm.wal.size.bytes", this, t -> (double) t.wal.sizeBytes())
                .register(registry);

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

        MetricsHttpServer.startIfEnabled();
    }

    /**
     * 插入键值对
     */
    public void put(String key, String value) throws IOException {
        long start = System.nanoTime();
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
            long end = System.nanoTime();
            metrics.recordWrite(end - start);
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
        long start = System.nanoTime();
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }

        lock.readLock().lock();
        try {
            KeyValue ent = activeMemTable.getEntry(key);
            if (ent != null) {
                if (ent.isDeleted())
                    return null;
                return ent.getValue();
            }

            // 2. 查询不可变MemTable（按时间倒序）
            for (int i = immutableMemTables.size() - 1; i >= 0; i--) {
                KeyValue e = immutableMemTables.get(i).getEntry(key);
                if (e != null) {
                    if (e.isDeleted())
                        return null;
                    return e.getValue();
                }
            }

            // 3. 查询SSTable（按创建时间倒序）
            List<SSTable> sortedSSTables = new ArrayList<>(ssTables);
            sortedSSTables.sort((a, b) -> Long.compare(b.getCreationTime(), a.getCreationTime()));

            for (SSTable ssTable : sortedSSTables) {
                KeyValue e = ssTable.getEntryRaw(key);
                if (e != null) {
                    if (e.isDeleted())
                        return null;
                    return e.getValue();
                }
            }

            return null;
        } finally {
            lock.readLock().unlock();
            long end = System.nanoTime();
            metrics.recordRead(end - start);
        }
    }

    public java.util.Iterator<KeyValue> range(String startKey, String endKey, boolean includeStart, boolean includeEnd)
            throws java.io.IOException {
        if (startKey != null && endKey != null && startKey.compareTo(endKey) > 0) {
            throw new IllegalArgumentException("startKey > endKey");
        }
        lock.readLock().lock();
        try {
            java.util.List<java.util.List<KeyValue>> sources = new java.util.ArrayList<>();
            sources.add(activeMemTable.getRangeEntriesRaw(startKey, endKey, includeStart, includeEnd));
            for (int i = immutableMemTables.size() - 1; i >= 0; i--) {
                sources.add(immutableMemTables.get(i).getRangeEntriesRaw(startKey, endKey, includeStart, includeEnd));
            }
            java.util.List<SSTable> tables = new java.util.ArrayList<>(ssTables);
            tables.sort((x, y) -> Long.compare(y.getCreationTime(), x.getCreationTime()));
            for (SSTable t : tables) {
                sources.add(t.getRangeEntries(startKey, endKey, includeStart, includeEnd));
            }

            java.util.List<KeyValue> out = new java.util.ArrayList<>();
            java.util.List<Integer> idx = new java.util.ArrayList<>();
            for (int i = 0; i < sources.size(); i++)
                idx.add(0);
            java.util.Comparator<int[]> cmp = (a, b) -> {
                KeyValue ka = sources.get(a[0]).get(a[1]);
                KeyValue kb = sources.get(b[0]).get(b[1]);
                int kc = ka.getKey().compareTo(kb.getKey());
                if (kc != 0)
                    return kc;
                return Long.compare(kb.getTimestamp(), ka.getTimestamp());
            };
            java.util.PriorityQueue<int[]> pq = new java.util.PriorityQueue<>(cmp);
            for (int s = 0; s < sources.size(); s++) {
                if (!sources.get(s).isEmpty())
                    pq.add(new int[] { s, 0 });
            }
            while (!pq.isEmpty()) {
                int[] top = pq.poll();
                KeyValue best = sources.get(top[0]).get(top[1]);
                String k = best.getKey();
                if (top[1] + 1 < sources.get(top[0]).size())
                    pq.add(new int[] { top[0], top[1] + 1 });
                while (!pq.isEmpty()) {
                    int[] n = pq.peek();
                    KeyValue kvn = sources.get(n[0]).get(n[1]);
                    if (!kvn.getKey().equals(k))
                        break;
                    pq.poll();
                    if (kvn.getTimestamp() > best.getTimestamp())
                        best = kvn;
                    if (n[1] + 1 < sources.get(n[0]).size())
                        pq.add(new int[] { n[0], n[1] + 1 });
                }
                if (!best.isDeleted())
                    out.add(best);
            }
            out.sort((x, y) -> x.getKey().compareTo(y.getKey()));
            return out.iterator();
        } finally {
            lock.readLock().unlock();
        }
    }

    public java.util.Iterator<KeyValue> rangeReverse(String startKey, String endKey) throws java.io.IOException {
        java.util.Iterator<KeyValue> it = range(startKey, endKey, true, true);
        java.util.List<KeyValue> list = new java.util.ArrayList<>();
        while (it.hasNext())
            list.add(it.next());
        list.sort((x, y) -> y.getKey().compareTo(x.getKey()));
        return list.iterator();
    }

    // 范围检查辅助方法 - 保留以备将来使用
    @SuppressWarnings("unused")
    private boolean inRange(String key, String startKey, String endKey, boolean includeStart, boolean includeEnd) {
        if (startKey != null) {
            int c = key.compareTo(startKey);
            if (c < 0 || (c == 0 && !includeStart))
                return false;
        }
        if (endKey != null) {
            int c = key.compareTo(endKey);
            if (c > 0 || (c == 0 && !includeEnd))
                return false;
        }
        return true;
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
            long flushStart = System.nanoTime();
            // 排序
            entries.sort(KeyValue::compareTo);

            // 创建SSTable文件
            String fileName = String.format("%s/sstable_level0_%d.db",
                    dataDir, System.currentTimeMillis());
            try {
                SSTable newSSTable = new SSTable(fileName, entries, compressionStrategy);
                ssTables.add(newSSTable);
                wal.checkpoint();
            } catch (IOException e) {
                metrics.recordFlushFailure();
                throw e;
            }
            long flushEnd = System.nanoTime();
            long bytes = new java.io.File(fileName).length();
            metrics.recordFlush(flushEnd - flushStart, bytes);
        }
    }

    /**
     * 启动后台压缩任务
     */
    // 后台压缩任务 - 暂时禁用以避免测试线程问题
    @SuppressWarnings("unused")
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
        long start = System.nanoTime();
        lock.writeLock().lock();
        long bytesBeforeCompaction = 0L;
        try {
            for (SSTable t : ssTables) {
                java.io.File f = new java.io.File(t.getFilePath());
                if (f.exists())
                    bytesBeforeCompaction += f.length();
            }
            List<SSTable> newSSTables;
            try {
                newSSTables = compactionStrategy.compact(ssTables);
            } catch (IOException e) {
                metrics.recordCompactionFailure();
                throw e;
            }
            ssTables.clear();
            ssTables.addAll(newSSTables);
        } finally {
            lock.writeLock().unlock();
            long end = System.nanoTime();
            long bytesOut = 0L;
            for (SSTable t : ssTables) {
                java.io.File f = new java.io.File(t.getFilePath());
                if (f.exists())
                    bytesOut += f.length();
            }
            long bytesCompacted = bytesOut;
            metrics.recordCompaction(end - start, bytesCompacted);

            // 调试信息：记录压缩效率
            if (bytesBeforeCompaction > 0) {
                double compressionRatio = (double) bytesOut / bytesBeforeCompaction;
                double spaceSaved = 1.0 - compressionRatio;
                System.out.printf(
                        "[DEBUG] Compaction completed: before=%,d bytes, after=%,d bytes, ratio=%.2f, saved=%.1f%%\n",
                        bytesBeforeCompaction, bytesOut, compressionRatio, spaceSaved * 100);
            }
        }
    }

    private int countLevel(int level) {
        int c = 0;
        for (SSTable t : ssTables) {
            String path = t.getFilePath();
            int idx = path.indexOf("level");
            if (idx >= 0) {
                int s = idx + 5;
                int e = path.indexOf('_', s);
                if (e > s) {
                    try {
                        int lv = Integer.parseInt(path.substring(s, e));
                        if (lv == level)
                            c++;
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return c;
    }

    public int getSSTableCount() {
        return ssTables.size();
    }

    public int getActiveMemTableSize() {
        return activeMemTable.size();
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

        MetricsHttpServer.stopIfRunning();
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
