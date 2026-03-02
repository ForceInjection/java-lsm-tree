package com.brianxiadong.lsmtree.cache;

import com.brianxiadong.lsmtree.KeyValue;
import java.util.Map;

/**
 * 缓存块
 * <p>
 * 存储属于同一个 Hash Bucket 的多个 KeyValue。
 * 用于 Block Cache，可以减少元数据开销并提高空间局部性。
 */
public class Block {
    private final String id;
    private final Map<String, KeyValue> entries;
    public Block(String id, Map<String, KeyValue> entries) { this.id = id; this.entries = entries; }
    public String getId() { return id; }
    public Map<String, KeyValue> getEntries() { return entries; }
}