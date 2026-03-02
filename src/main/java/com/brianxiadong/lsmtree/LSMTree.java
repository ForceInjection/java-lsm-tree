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
    private final Object memTableLock = new Object(); // 专门用于 MemTable 切换的锁

    // 磁盘组件
    private final List<SSTable> ssTables;
    private final java.util.concurrent.atomic.AtomicLong fileSequence = new java.util.concurrent.atomic.AtomicLong(0);

    // 后台任务
    private final ExecutorService compactionExecutor;
    private final CompactionStrategy compactionStrategy;
    private final CompressionStrategy compressionStrategy;
    private final LSMTreeMetrics metrics;
    private com.brianxiadong.lsmtree.cache.CacheManager cacheManager;

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
        this.wal = new WriteAheadLog(dataDir + "/wal.log", Integer.getInteger("lsm.wal.sync.batch", 64), Long.getLong("lsm.wal.sync.interval.ms", 50L));

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

    public void setCacheManager(com.brianxiadong.lsmtree.cache.CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * 插入键值对
     * 流程：
     * 1. 获取写锁
     * 2. 写入 WAL (Write-Ahead Log) 保证持久性
     * 3. 写入内存表 (MemTable)
     * 4. 检查是否需要 Flush (MemTable -> Immutable -> SSTable)
     * 5. 更新缓存 (如果启用)
     */
    public void put(String key, String value) throws IOException {
        long start = System.nanoTime();
        if (key == null || value == null) {
            throw new IllegalArgumentException("Key and value cannot be null");
        }

        lock.writeLock().lock();
        try {
            // 写入WAL
            WriteAheadLog.LogEntry entry = WriteAheadLog.LogEntry.put(key, value);
            wal.append(entry);

            // 写入活跃MemTable (使用相同的timestamp)
            activeMemTable.put(new KeyValue(key, value, entry.getTimestamp(), false));

            // 检查是否需要刷盘
            if (activeMemTable.shouldFlush()) {
                flushMemTable();
            }
            if (cacheManager != null) {
                com.brianxiadong.lsmtree.KeyValue kv = new com.brianxiadong.lsmtree.KeyValue(key, value, System.currentTimeMillis(), false);
                try {
                    cacheManager.put(key, kv, com.brianxiadong.lsmtree.cache.CacheType.ROW);
                } catch (com.brianxiadong.lsmtree.cache.CacheException ignored) {}
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
            WriteAheadLog.LogEntry entry = WriteAheadLog.LogEntry.delete(key);
            wal.append(entry);

            // 在活跃MemTable中标记删除 (使用相同的timestamp)
            activeMemTable.put(new KeyValue(key, null, entry.getTimestamp(), true));

            // 检查是否需要刷盘
            if (activeMemTable.shouldFlush()) {
                flushMemTable();
            }
            if (cacheManager != null) {
                com.brianxiadong.lsmtree.KeyValue kv = com.brianxiadong.lsmtree.KeyValue.createTombstone(key);
                try {
                    cacheManager.put(key, kv, com.brianxiadong.lsmtree.cache.CacheType.ROW);
                } catch (com.brianxiadong.lsmtree.cache.CacheException ignored) {}
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 查询键值
     * 流程：
     * 1. 获取读锁
     * 2. 查询缓存 (如果启用)
     * 3. 查询活跃 MemTable
     * 4. 查询不可变 MemTable 列表 (按时间倒序)
     * 5. 查询 SSTable 列表 (按时间倒序，使用布隆过滤器加速)
     */
    public String get(String key) {
        long start = System.nanoTime();
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }

        lock.readLock().lock();
        try {
            if (cacheManager != null) {
                try {
                    Object obj = cacheManager.get(key, com.brianxiadong.lsmtree.cache.CacheType.ROW);
                    if (obj instanceof com.brianxiadong.lsmtree.KeyValue) {
                        com.brianxiadong.lsmtree.KeyValue ck = (com.brianxiadong.lsmtree.KeyValue) obj;
                        if (ck.isDeleted()) return null;
                        return ck.getValue();
                    }
                } catch (com.brianxiadong.lsmtree.cache.CacheException ignored) {}
            }
            KeyValue ent = activeMemTable.getEntry(key);
            if (ent != null) {
                if (ent.isDeleted())
                    return null;
                if (cacheManager != null) {
                    try { cacheManager.put(key, ent, com.brianxiadong.lsmtree.cache.CacheType.ROW); } catch (com.brianxiadong.lsmtree.cache.CacheException ignored) {}
                }
                return ent.getValue();
            }

            // 2. 查询不可变MemTable（按时间倒序）
            List<MemTable> immutableCopy;
            synchronized (memTableLock) {
                immutableCopy = new ArrayList<>(immutableMemTables);
            }
            for (int i = immutableCopy.size() - 1; i >= 0; i--) {
                KeyValue e = immutableCopy.get(i).getEntry(key);
                if (e != null) {
                    if (e.isDeleted())
                        return null;
                    if (cacheManager != null) {
                        try { cacheManager.put(key, e, com.brianxiadong.lsmtree.cache.CacheType.ROW); } catch (com.brianxiadong.lsmtree.cache.CacheException ignored) {}
                    }
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
                    if (cacheManager != null) {
                        try { cacheManager.put(key, e, com.brianxiadong.lsmtree.cache.CacheType.ROW); } catch (com.brianxiadong.lsmtree.cache.CacheException ignored) {}
                    }
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

    public Iterator<KeyValue> range(String startKey, String endKey) {
        return range(startKey, endKey, true, false);
    }

    public Iterator<KeyValue> range(String startKey, String endKey, boolean includeStart, boolean includeEnd) {
        lock.readLock().lock();
        try {
            // 多路归并排序
            // 每个源是一个 List<KeyValue>
            // 顺序: Active MemTable -> Immutable MemTables (Newest to Oldest) -> SSTables (Newest to Oldest)
            java.util.List<java.util.List<KeyValue>> sources = new java.util.ArrayList<>();
            sources.add(activeMemTable.getRangeEntriesRaw(startKey, endKey, includeStart, includeEnd));
            
            java.util.List<MemTable> immutableCopy;
            synchronized (memTableLock) {
                immutableCopy = new java.util.ArrayList<>(immutableMemTables);
            }
            for (int i = immutableCopy.size() - 1; i >= 0; i--) {
                sources.add(immutableCopy.get(i).getRangeEntriesRaw(startKey, endKey, includeStart, includeEnd));
            }
            
            java.util.List<SSTable> tables = new java.util.ArrayList<>(ssTables);
            // 假设 ssTables 是按创建时间排序的? (通常是的，越新越后面)
            // 我们需要从新到旧遍历
            for (int i = tables.size() - 1; i >= 0; i--) {
                try {
                    sources.add(tables.get(i).getRangeEntries(startKey, endKey, includeStart, includeEnd)); 
                } catch (IOException e) {
                    throw new RuntimeException("Error reading SSTable", e);
                }
            }
            
            java.util.List<KeyValue> out = new java.util.ArrayList<>();
            
            // 使用优先队列进行归并
            // 元素: [sourceIndex, elementIndex]
            java.util.PriorityQueue<int[]> pq = new java.util.PriorityQueue<>(
                (a, b) -> {
                    KeyValue ka = sources.get(a[0]).get(a[1]);
                    KeyValue kb = sources.get(b[0]).get(b[1]);
                    int kc = ka.getKey().compareTo(kb.getKey());
                    if (kc != 0) return kc;
                    // Key 相同，Timestamp 大的优先
                    int tc = Long.compare(kb.getTimestamp(), ka.getTimestamp());
                    if (tc != 0) return tc;
                    // Timestamp 相同，sourceIndex 小的优先 (Newer source)
                    return Integer.compare(a[0], b[0]);
                }
            );
            for (int s = 0; s < sources.size(); s++) {
                if (!sources.get(s).isEmpty())
                    pq.add(new int[] { s, 0 });
            }
            while (!pq.isEmpty()) {
                int[] top = pq.poll();
                KeyValue best = sources.get(top[0]).get(top[1]);
                int bestSource = top[0];
                String k = best.getKey();
                if (top[1] + 1 < sources.get(top[0]).size())
                    pq.add(new int[] { top[0], top[1] + 1 });
                while (!pq.isEmpty()) {
                    int[] n = pq.peek();
                    KeyValue kvn = sources.get(n[0]).get(n[1]);
                    if (!kvn.getKey().equals(k))
                        break;
                    pq.poll();
                    if (kvn.getTimestamp() > best.getTimestamp()
                            || (kvn.getTimestamp() == best.getTimestamp() && n[0] < bestSource)) {
                        best = kvn;
                        bestSource = n[0];
                    }
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
        synchronized (memTableLock) {
            if (activeMemTable.isEmpty()) {
                return;
            }

            // 将活跃MemTable转为不可变
            immutableMemTables.add(activeMemTable);
            activeMemTable = new MemTable(memTableMaxSize);
        }

        // 同步刷盘，避免死锁
        flushImmutableMemTable();
    }

    /**
     * 刷新不可变MemTable到SSTable（调用前必须已获取写锁）
     */
    private void flushImmutableMemTable() throws IOException {
        MemTable memTable;
        synchronized (memTableLock) {
            if (immutableMemTables.isEmpty()) {
                return;
            }
            memTable = immutableMemTables.remove(0);
        }

        List<KeyValue> entries = memTable.getAllEntries();

        if (!entries.isEmpty()) {
            long flushStart = System.nanoTime();
            // 排序
            entries.sort(KeyValue::compareTo);

            // 创建SSTable文件
            // 使用 atomic counter 防止在高并发 flush 时产生文件名冲突
            String fileName = String.format("%s/sstable_level0_%d_%d.db",
                    dataDir, System.currentTimeMillis(), fileSequence.getAndIncrement());
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
                activeMemTable.put(new KeyValue(entry.getKey(), entry.getValue(), entry.getTimestamp(), false));
            } else if (entry.getOperation() == WriteAheadLog.Operation.DELETE) {
                activeMemTable.put(new KeyValue(entry.getKey(), null, entry.getTimestamp(), true));
            }
        }
    }

    /**
     * 强制刷新所有数据到磁盘（包括 MemTable 和 WAL 检查点）
     */
    public void flush() throws IOException {
        lock.writeLock().lock();
        try {
            // 刷新活跃MemTable
            synchronized (memTableLock) {
                if (!activeMemTable.isEmpty()) {
                    // 将活跃MemTable转为不可变
                    immutableMemTables.add(activeMemTable);
                    activeMemTable = new MemTable(memTableMaxSize);
                }
            }

            // 刷新所有剩余的不可变MemTable
            while (true) {
                synchronized (memTableLock) {
                    if (immutableMemTables.isEmpty()) break;
                }
                flushImmutableMemTable();
            }
            
            // 创建WAL检查点（清空WAL）
            wal.checkpoint();
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

        // 关闭压缩线程池，等待线程终止
        compactionExecutor.shutdownNow();
        try {
            if (!compactionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                // 如果线程未能在超时内终止，记录警告但不阻塞
                System.err.println("警告: 压缩线程未能在超时内终止");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        MetricsHttpServer.stopIfRunning();

        try { AsyncIO.closeDefault(); } catch (IOException ignored) {}
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
            int immutableCount;
            synchronized (memTableLock) {
                immutableCount = immutableMemTables.size();
            }
            return new LSMTreeStats(
                    activeMemTable.size(),
                    immutableCount,
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
