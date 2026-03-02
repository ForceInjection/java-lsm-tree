package com.brianxiadong.lsmtree;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Sorted String Table (SSTable) 实现
 * 磁盘上的有序不可变文件
 */
public class SSTable {
    private final String filePath;
    private final BloomFilter bloomFilter;
    private final long creationTime;

    public SSTable(String filePath, List<KeyValue> sortedData) throws IOException {
        this.filePath = filePath;
        this.creationTime = System.currentTimeMillis();
        this.bloomFilter = new BloomFilter(sortedData.size(), 0.01);

        writeToFile(sortedData);
    }

    public SSTable(String filePath, List<KeyValue> sortedData, CompressionStrategy compression) throws IOException {
        this.filePath = filePath;
        this.creationTime = System.currentTimeMillis();
        this.bloomFilter = new BloomFilter(sortedData.size(), 0.01);
        writeToFile(sortedData, compression);
    }

    /**
     * 从文件路径加载已存在的SSTable
     * 注意：这里会重新构建布隆过滤器，需要读取整个文件，对于大文件可能耗时较长。
     * 优化方案：将布隆过滤器序列化存储在 SSTable 文件头或尾部。
     */
    public SSTable(String filePath) throws IOException {
        this.filePath = filePath;
        this.creationTime = Files.getLastModifiedTime(Paths.get(filePath)).toMillis();
        this.bloomFilter = new BloomFilter(1000, 0.01);

        // 重新构建布隆过滤器
        rebuildBloomFilter();
    }

    /**
     * 重新构建布隆过滤器
     */
    private void rebuildBloomFilter() throws IOException {
        try (DataInputStream dis = openPayloadInput()) {
            int totalEntries = dis.readInt();
            for (int i = 0; i < totalEntries; i++) {
                String key = dis.readUTF();
                boolean deleted = dis.readBoolean();
                if (!deleted) {
                    dis.readUTF();
                }
                dis.readLong();
                bloomFilter.add(key);
            }
        }
    }

    /**
     * 将排序数据写入文件
     * 使用同步 I/O 避免创建额外的异步线程
     */
    private void writeToFile(List<KeyValue> sortedData) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(baos))) {
            dos.writeInt(sortedData.size());
            for (KeyValue kv : sortedData) {
                bloomFilter.add(kv.getKey());
                dos.writeUTF(kv.getKey());
                dos.writeBoolean(kv.isDeleted());
                if (!kv.isDeleted()) {
                    dos.writeUTF(kv.getValue());
                }
                dos.writeLong(kv.getTimestamp());
            }
        }
        byte[] payload = baos.toByteArray();
        // 使用同步 I/O 写入，避免 AsynchronousFileChannel 创建额外线程
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(filePath, "rw");
             java.nio.channels.FileChannel fc = raf.getChannel()) {
            fc.write(java.nio.ByteBuffer.wrap(payload));
            fc.force(true); // 确保数据刷新到磁盘
        }
    }

    private void writeToFile(List<KeyValue> sortedData, CompressionStrategy compression) throws IOException {
        if (compression == null || "NONE".equals(compression.getType())) {
            writeToFile(sortedData);
            return;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(baos))) {
            dos.writeInt(sortedData.size());
            for (KeyValue kv : sortedData) {
                bloomFilter.add(kv.getKey());
                dos.writeUTF(kv.getKey());
                dos.writeBoolean(kv.isDeleted());
                if (!kv.isDeleted()) {
                    dos.writeUTF(kv.getValue());
                }
                dos.writeLong(kv.getTimestamp());
            }
        }
        byte[] payload = baos.toByteArray();
        byte[] compressed = compression.compress(payload);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(bos))) {
            out.writeBytes("LSM1");
            String type = compression.getType();
            String four = (type + "    ").substring(0, 4);
            out.writeBytes(four);
            out.write(compressed);
        }
        byte[] all = bos.toByteArray();
        // 使用同步 I/O 写入，避免 AsynchronousFileChannel 创建额外线程
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(filePath, "rw");
             java.nio.channels.FileChannel fc = raf.getChannel()) {
            fc.write(java.nio.ByteBuffer.wrap(all));
            fc.force(true); // 确保数据刷新到磁盘
        }
    }

    private static final long MAP_THRESHOLD_BYTES = Long.getLong("lsm.sstable.map.threshold.bytes", 16L * 1024 * 1024);

    private DataInputStream openPayloadInput() throws IOException {
        FileInputStream fis = new FileInputStream(filePath);
        BufferedInputStream bis = new BufferedInputStream(fis);
        bis.mark(8);
        byte[] magic = new byte[4];
        int r = bis.read(magic);
        if (r == 4 && magic[0] == 'L' && magic[1] == 'S' && magic[2] == 'M' && magic[3] == '1') {
            byte[] type = new byte[4];
            int bytesRead = bis.read(type);
            if (bytesRead != 4) {
                throw new IOException("Failed to read compression type, expected 4 bytes but got " + bytesRead);
            }
            ByteArrayOutputStream rest = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = bis.read(buf)) != -1)
                rest.write(buf, 0, n);
            bis.close();
            String t = new String(type, "UTF-8");
            if ("LZ4".equals(t.trim())) {
                byte[] decompressed = new LZ4CompressionStrategy().decompress(rest.toByteArray());
                return new DataInputStream(new BufferedInputStream(new ByteArrayInputStream(decompressed)));
            } else {
                return new DataInputStream(new BufferedInputStream(new ByteArrayInputStream(rest.toByteArray())));
            }
        } else {
            bis.reset();
            long size = new java.io.File(filePath).length();
            if (size >= MAP_THRESHOLD_BYTES) {
                bis.close();
                try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(filePath, "r"); java.nio.channels.FileChannel fc = raf.getChannel()) {
                    java.nio.MappedByteBuffer mbb = fc.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, size);
                    return new DataInputStream(new BufferedInputStream(new ByteBufferBackedInputStream(mbb)));
                }
            }
            return new DataInputStream(bis);
        }
    }

    private static class ByteBufferBackedInputStream extends InputStream {
        private final java.nio.ByteBuffer buf;
        ByteBufferBackedInputStream(java.nio.ByteBuffer buf) { this.buf = buf; }
        @Override public int read() { if (!buf.hasRemaining()) return -1; return buf.get() & 0xFF; }
        @Override public int read(byte[] bytes, int off, int len) { if (!buf.hasRemaining()) return -1; int toRead = Math.min(len, buf.remaining()); buf.get(bytes, off, toRead); return toRead; }
    }

    /**
     * 查询键值 - 简化实现，顺序搜索
     * 注意：这是一个 O(N) 的操作，虽然有布隆过滤器优化，但对于大文件仍然较慢。
     * 生产环境通常会使用稀疏索引 (Sparse Index) 来加速查找，将复杂度降低到 O(log N) 或 O(1) (block seek)。
     */
    public String get(String key) {
        // 首先检查布隆过滤器
        if (!bloomFilter.mightContain(key)) {
            return null;
        }
        try (DataInputStream dis = openPayloadInput()) {
            int totalEntries = dis.readInt();
            // 顺序搜索所有条目
            for (int i = 0; i < totalEntries; i++) {
                String currentKey = dis.readUTF();
                boolean deleted = dis.readBoolean();
                String value = null;
                if (!deleted) {
                    value = dis.readUTF();
                }

                // 读取时间戳但不使用（为了向前兼容文件格式）
                dis.readLong();
                if (currentKey.equals(key)) {
                    return deleted ? null : value;
                }

                // 由于数据有序，如果当前键大于目标键，则不存在
                if (currentKey.compareTo(key) > 0) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public KeyValue getEntryRaw(String key) {
        if (!bloomFilter.mightContain(key)) {
            return null;
        }
        try (DataInputStream dis = openPayloadInput()) {
            int totalEntries = dis.readInt();
            for (int i = 0; i < totalEntries; i++) {
                String currentKey = dis.readUTF();
                boolean deleted = dis.readBoolean();
                String value = null;
                if (!deleted) {
                    value = dis.readUTF();
                }
                long timestamp = dis.readLong();
                if (currentKey.equals(key)) {
                    return new KeyValue(currentKey, value, timestamp, deleted);
                }
                if (currentKey.compareTo(key) > 0) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取所有键值对（用于合并）
     */
    public List<KeyValue> getAllEntries() throws IOException {
        List<KeyValue> entries = new ArrayList<>();

        try (DataInputStream dis = openPayloadInput()) {

            int totalEntries = dis.readInt();

            for (int i = 0; i < totalEntries; i++) {
                String key = dis.readUTF();
                boolean deleted = dis.readBoolean();
                String value = null;
                if (!deleted) {
                    value = dis.readUTF();
                }
                long timestamp = dis.readLong();

                entries.add(new KeyValue(key, value, timestamp, deleted));
            }
        }

        return entries;
    }

    public List<KeyValue> range(String startKey, String endKey) throws IOException {
        return getRangeEntries(startKey, endKey, true, false);
    }

    public List<KeyValue> getRangeEntries(String startKey, String endKey, boolean includeStart, boolean includeEnd)
            throws IOException {
        List<KeyValue> entries = new ArrayList<>();
        try (DataInputStream dis = openPayloadInput()) {
            int totalEntries = dis.readInt();
            for (int i = 0; i < totalEntries; i++) {
                String key = dis.readUTF();
                boolean deleted = dis.readBoolean();
                String value = null;
                if (!deleted) {
                    value = dis.readUTF();
                }
                long timestamp = dis.readLong();

                int s = startKey == null ? 1 : key.compareTo(startKey);
                if (s < 0 || (s == 0 && !includeStart)) {
                    continue;
                }
                int e = endKey == null ? -1 : key.compareTo(endKey);
                if (e > 0 || (e == 0 && !includeEnd)) {
                    break;
                }
                entries.add(new KeyValue(key, value, timestamp, deleted));
            }
        }
        return entries;
    }

    /**
     * 删除SSTable文件
     */
    public void delete() throws IOException {
        Files.deleteIfExists(Paths.get(filePath));
    }

    public String getFilePath() {
        return filePath;
    }

    public long getCreationTime() {
        return creationTime;
    }
}
