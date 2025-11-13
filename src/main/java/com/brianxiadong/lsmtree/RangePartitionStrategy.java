package com.brianxiadong.lsmtree;

import java.util.ArrayList;
import java.util.List;

public class RangePartitionStrategy implements PartitionStrategy {
    private final List<String> boundaries; // sorted exclusive upper bounds per partition except last

    public RangePartitionStrategy(List<String> boundaries) {
        this.boundaries = boundaries;
    }

    @Override
    public int getPartition(String key, int numPartitions) {
        if (boundaries == null || boundaries.isEmpty()) return 0;
        for (int i = 0; i < boundaries.size(); i++) {
            String b = boundaries.get(i);
            if (key.compareTo(b) <= 0) return i;
        }
        return boundaries.size();
    }

    @Override
    public List<Integer> getPartitionsForRange(String startKey, String endKey, int numPartitions) {
        List<Integer> res = new ArrayList<>();
        int start = 0;
        int end = numPartitions - 1;
        if (startKey != null) start = getPartition(startKey, numPartitions);
        if (endKey != null) end = getPartition(endKey, numPartitions);
        for (int i = Math.min(start, end); i <= Math.max(start, end); i++) res.add(i);
        return res;
    }
}

