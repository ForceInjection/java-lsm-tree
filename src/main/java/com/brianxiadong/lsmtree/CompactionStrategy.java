package com.brianxiadong.lsmtree;

import java.io.IOException;
import java.util.List;

public interface CompactionStrategy {
    boolean needsCompaction(List<SSTable> ssTables);
    List<SSTable> compact(List<SSTable> ssTables) throws IOException;
    LeveledCompactionStrategy.CompactionTask selectCompactionTask(List<SSTable> ssTables);
    void setCompressionStrategy(CompressionStrategy compressionStrategy);
}
