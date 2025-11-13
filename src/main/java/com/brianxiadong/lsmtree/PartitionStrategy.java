package com.brianxiadong.lsmtree;

import java.util.List;

public interface PartitionStrategy {
    int getPartition(String key, int numPartitions);
    List<Integer> getPartitionsForRange(String startKey, String endKey, int numPartitions);
}

