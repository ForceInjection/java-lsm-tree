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
     */
    private void writeToFile(List<KeyValue> sortedData) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(filePath)))) {

            // 写入条目数量
            dos.writeInt(sortedData.size());

            // 写入所有数据条目
            for (KeyValue kv : sortedData) {
                // 添加到布隆过滤器
                bloomFilter.add(kv.getKey());

                // 写入数据：key, deleted, value(如果不是删除), timestamp
                dos.writeUTF(kv.getKey());
                dos.writeBoolean(kv.isDeleted());
                if (!kv.isDeleted()) {
                    dos.writeUTF(kv.getValue());
                }
                dos.writeLong(kv.getTimestamp());
            }
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
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(filePath)))) {
            out.writeBytes("LSM1");
            String type = compression.getType();
            String four = (type + "    ").substring(0, 4);
            out.writeBytes(four);
            out.write(compressed);
        }
    }

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
            return new DataInputStream(bis);
        }
    }

    /**
     * 查询键值 - 简化实现，顺序搜索
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
