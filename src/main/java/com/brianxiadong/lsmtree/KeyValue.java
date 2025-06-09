package com.brianxiadong.lsmtree;

/**
 * LSM Tree中的键值对数据结构
 * 包含键、值和时间戳信息
 */
public class KeyValue implements Comparable<KeyValue> {
    private final String key;
    private final String value;
    private final long timestamp;
    private final boolean deleted; // 标记是否为删除操作

    public KeyValue(String key, String value) {
        this(key, value, System.currentTimeMillis(), false);
    }

    public KeyValue(String key, String value, long timestamp, boolean deleted) {
        this.key = key;
        this.value = value;
        this.timestamp = timestamp;
        this.deleted = deleted;
    }

    /**
     * 创建删除标记的键值对
     */
    public static KeyValue createTombstone(String key) {
        return new KeyValue(key, null, System.currentTimeMillis(), true);
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public int compareTo(KeyValue other) {
        int keyCompare = this.key.compareTo(other.key);
        if (keyCompare != 0) {
            return keyCompare;
        }
        // 如果键相同，按时间戳降序排列（新的在前）
        return Long.compare(other.timestamp, this.timestamp);
    }

    @Override
    public String toString() {
        return String.format("KeyValue{key='%s', value='%s', timestamp=%d, deleted=%s}",
                key, value, timestamp, deleted);
    }
}