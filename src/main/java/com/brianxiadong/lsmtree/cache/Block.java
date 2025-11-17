package com.brianxiadong.lsmtree.cache;

import com.brianxiadong.lsmtree.KeyValue;
import java.util.Map;

public class Block {
    private final String id;
    private final Map<String, KeyValue> entries;
    public Block(String id, Map<String, KeyValue> entries) { this.id = id; this.entries = entries; }
    public String getId() { return id; }
    public Map<String, KeyValue> getEntries() { return entries; }
}